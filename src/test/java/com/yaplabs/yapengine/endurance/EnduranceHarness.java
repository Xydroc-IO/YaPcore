package com.yaplabs.yapengine.endurance;

import com.yapcore.util.ThreadMetrics;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Months-long reliability soak for Threads 7–8 + sequence retention.
 * Uses <strong>stable</strong> stream keys ({@code bot-0..N}) so STREAM_SEQ
 * cannot grow without bound. Samples LIVE / leases / heap / threads and
 * writes an actionable HTML+JSON report under {@code logs/endurance/}.
 *
 * <pre>
 *   gradle endurance -Dyap.endurance.seconds=300 -Dyap.endurance.bots=64
 *   gradle endurance / chassis harness (see docs/start/TESTING.md)
 * </pre>
 */
public final class EnduranceHarness {

    public record Sample(
            long elapsedMs,
            long submitted,
            long processed,
            int pending,
            int liveTokens,
            int streamKeys,
            int leaseMap,
            int metricKeys,
            int threads,
            long heapUsedMb,
            long heapMaxMb,
            long retried
    ) {
    }

    public record Finding(String severity, String code, String detail, String fixHint) {
    }

    public static void main(String[] args) throws Exception {
        int bots = Integer.getInteger("yap.endurance.bots", 64);
        int seconds = Integer.getInteger("yap.endurance.seconds", 120);
        int sampleMs = Integer.getInteger("yap.endurance.sampleMs", 5_000);
        int idleSeconds = Integer.getInteger("yap.endurance.idleSeconds", 15);
        int maxPending = Integer.getInteger("yap.endurance.maxPending", 2_000);
        Path reportDir = Path.of(System.getProperty("yap.endurance.reportDir",
                System.getProperty("yapcore.home", ".") + "/logs/endurance"));

        System.out.println("EnduranceHarness bots=" + bots
                + " loadSeconds=" + seconds
                + " idleSeconds=" + idleSeconds
                + " maxPending=" + maxPending
                + " reportDir=" + reportDir);

        SequenceToken.resetForTests();
        ThreadMetrics.resetForTests();

        ChunkSyncLayer layer = new ChunkSyncLayer();
        layer.start();

        LongAdder submitted = new LongAdder();
        LongAdder applied = new LongAdder();
        LongAdder backpressured = new LongAdder();
        AtomicBoolean load = new AtomicBoolean(true);
        List<Sample> samples = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        Thread[] workers = new Thread[bots];
        for (int i = 0; i < bots; i++) {
            int botId = i;
            workers[i] = new Thread(() -> {
                long n = 0;
                String stream = "bot-" + botId;
                String inv = "inv:bot-" + (botId % Math.max(1, bots / 4));
                while (load.get()) {
                    var handoff = new ChunkSyncLayer.Handoff(
                            stream + "-" + n,
                            inv,
                            SpatialQuadrant.byId((int) (n & 3)),
                            SpatialQuadrant.byId((int) ((n + 1) & 3)),
                            SequenceToken.next(stream),
                            applied::increment
                    );
                    if (layer.trySubmitHandoff(handoff, maxPending)) {
                        submitted.increment();
                        n++;
                    } else {
                        // Token was created but not queued — forget immediately
                        handoff.token().forget();
                        backpressured.increment();
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }, "endurance-bot-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
        }

        long start = System.currentTimeMillis();
        long loadUntil = start + seconds * 1000L;
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        while (System.currentTimeMillis() < loadUntil) {
            Thread.sleep(sampleMs);
            samples.add(takeSample(start, layer, submitted, memory, threads));
            printSample(samples.get(samples.size() - 1));
        }

        load.set(false);
        for (Thread t : workers) {
            t.join(10_000);
        }

        System.out.println("Load stopped. backpressured=" + backpressured.sum()
                + " — draining queues…");

        // Drain until empty (or timeout) before retention assertions
        long drainDeadline = System.currentTimeMillis() + Math.max(60_000L, idleSeconds * 1000L);
        while (layer.pending() > 0 && System.currentTimeMillis() < drainDeadline) {
            Thread.sleep(200);
            SequenceToken.pruneOlderThan(60_000_000L);
            layer.leases().pruneEmpty();
            if ((System.currentTimeMillis() - start) % 5000 < 250) {
                samples.add(takeSample(start, layer, submitted, memory, threads));
                printSample(samples.get(samples.size() - 1));
            }
        }

        // Quiet idle window after drain
        long idleUntil = System.currentTimeMillis() + idleSeconds * 1000L;
        while (System.currentTimeMillis() < idleUntil) {
            Thread.sleep(Math.min(sampleMs, 2_000));
            SequenceToken.pruneOlderThan(30_000_000L);
            layer.leases().pruneEmpty();
            samples.add(takeSample(start, layer, submitted, memory, threads));
            printSample(samples.get(samples.size() - 1));
        }

        Sample last = takeSample(start, layer, submitted, memory, threads);
        samples.add(last);
        Sample mid = samples.get(Math.max(0, samples.size() / 2));

        analyze(findings, layer, submitted, applied, samples, mid, last, bots, backpressured.sum());
        layer.stop();

        Files.createDirectories(reportDir);
        String stamp = Instant.now().toString().replace(':', '-');
        Path json = reportDir.resolve("endurance-" + stamp + ".json");
        Path html = reportDir.resolve("endurance-" + stamp + ".html");
        Path latestJson = reportDir.resolve("latest.json");
        Path latestHtml = reportDir.resolve("latest.html");
        writeJson(json, findings, samples, last, bots, seconds);
        writeHtml(html, findings, samples, last, bots, seconds);
        Files.writeString(latestJson, Files.readString(json));
        Files.writeString(latestHtml, Files.readString(html));

        System.out.println();
        System.out.println("Report: " + html.toAbsolutePath());
        System.out.println("JSON:   " + json.toAbsolutePath());
        boolean failed = findings.stream().anyMatch(f -> "FAIL".equals(f.severity()));
        for (Finding f : findings) {
            System.out.println("[" + f.severity() + "] " + f.code() + " — " + f.detail());
            System.out.println("         fix: " + f.fixHint());
        }
        System.exit(failed ? 1 : 0);
    }

    private static Sample takeSample(long startMs, ChunkSyncLayer layer, LongAdder submitted,
                                     MemoryMXBean memory, ThreadMXBean threads) {
        long used = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long max = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        return new Sample(
                System.currentTimeMillis() - startMs,
                submitted.sum(),
                layer.getProcessed(),
                layer.pending(),
                SequenceToken.liveSize(),
                SequenceToken.streamKeyCount(),
                layer.leases().size(),
                ThreadMetrics.keyCount(),
                threads.getThreadCount(),
                used,
                max,
                layer.boundary().getRetried()
        );
    }

    private static void printSample(Sample s) {
        System.out.printf(
                "t=%4.1fs sub=%d proc=%d pend=%d live=%d streams=%d leases=%d metrics=%d thr=%d heap=%d/%dMB retry=%d%n",
                s.elapsedMs() / 1000.0,
                s.submitted(), s.processed(), s.pending(),
                s.liveTokens(), s.streamKeys(), s.leaseMap(), s.metricKeys(),
                s.threads(), s.heapUsedMb(), s.heapMaxMb(), s.retried()
        );
    }

    private static void analyze(List<Finding> findings, ChunkSyncLayer layer, LongAdder submitted,
                                LongAdder applied, List<Sample> samples, Sample mid, Sample last,
                                int bots, long backpressured) {
        long sub = submitted.sum();
        long proc = layer.getProcessed();
        if (layer.pending() > 0) {
            findings.add(new Finding("FAIL", "QUEUE_BACKLOG",
                    "pending=" + layer.pending() + " after drain window",
                    "DLM/boundary not draining; possible deadlock or lease starvation."));
        }
        if (proc < sub * 0.98) {
            findings.add(new Finding("FAIL", "HANDOFF_LOSS",
                    "processed=" + proc + " submitted=" + sub + " pending=" + layer.pending(),
                    "Inspect BoundaryArbitrator / ChunkSyncDlm queues; check for stuck leases."));
        } else {
            findings.add(new Finding("OK", "HANDOFF_OK",
                    "processed=" + proc + " submitted=" + sub
                            + " backpressured=" + backpressured, "—"));
        }

        if (last.streamKeys() > bots + 4) {
            findings.add(new Finding("FAIL", "STREAM_KEY_LEAK",
                    "streamKeys=" + last.streamKeys() + " expected≈" + bots,
                    "Use stable SequenceToken stream keys; call SequenceToken.forgetStream on disconnect."));
        } else {
            findings.add(new Finding("OK", "STREAM_KEYS_BOUNDED",
                    "streamKeys=" + last.streamKeys() + " bots=" + bots, "—"));
        }

        // After full drain, LIVE should be near zero
        if (last.liveTokens() > 64) {
            findings.add(new Finding("FAIL", "LIVE_TOKEN_RETENTION",
                    "liveTokens=" + last.liveTokens() + " after idle drain",
                    "Ensure token.forget() in finally (BoundaryArbitrator) and all ingest paths."));
        } else {
            findings.add(new Finding("OK", "LIVE_DRAINED",
                    "liveTokens=" + last.liveTokens(), "—"));
        }

        if (last.metricKeys() > 200) {
            findings.add(new Finding("FAIL", "METRIC_CARDINALITY",
                    "metricKeys=" + last.metricKeys(),
                    "Do not put player/sku/counts into ThreadMetrics action strings; use bump with fixed names."));
        } else {
            findings.add(new Finding("OK", "METRICS_BOUNDED",
                    "metricKeys=" + last.metricKeys(), "—"));
        }

        if (mid.heapUsedMb() > 0 && last.heapUsedMb() > mid.heapUsedMb() * 2 + 128) {
            findings.add(new Finding("WARN", "HEAP_GROWTH",
                    "midHeap=" + mid.heapUsedMb() + "MB endHeap=" + last.heapUsedMb() + "MB",
                    "Open logs/jfr soak recording or run heap-dump.sh → Eclipse MAT Leak Suspects."));
        }

        if (last.threads() > mid.threads() + 32) {
            findings.add(new Finding("WARN", "THREAD_GROWTH",
                    "midThreads=" + mid.threads() + " endThreads=" + last.threads(),
                    "Check ExecutorService shutdown (HTTP pack server, schedulers)."));
        }

        if (last.leaseMap() > bots + 16) {
            findings.add(new Finding("WARN", "LEASE_MAP_GROWTH",
                    "leaseMap=" + last.leaseMap(),
                    "Call AtomicLeaseManager.pruneEmpty() periodically; reuse inventory keys."));
        }

        if (findings.stream().noneMatch(f -> "FAIL".equals(f.severity()))) {
            findings.add(0, new Finding("OK", "ENDURANCE_PASS",
                    "No FAIL findings across " + samples.size() + " samples",
                    "Extend -Dyap.endurance.seconds toward 43200 (12h) for a long chassis soak."));
        }
    }

    private static void writeJson(Path path, List<Finding> findings, List<Sample> samples,
                                  Sample last, int bots, int seconds) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generated\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"bots\": ").append(bots).append(",\n");
        sb.append("  \"loadSeconds\": ").append(seconds).append(",\n");
        sb.append("  \"final\": ").append(sampleJson(last)).append(",\n");
        sb.append("  \"findings\": [\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("    {\"severity\":\"").append(f.severity())
                    .append("\",\"code\":\"").append(f.code())
                    .append("\",\"detail\":").append(jsonStr(f.detail()))
                    .append(",\"fixHint\":").append(jsonStr(f.fixHint())).append("}");
            if (i + 1 < findings.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ],\n  \"samples\": [\n");
        for (int i = 0; i < samples.size(); i++) {
            sb.append("    ").append(sampleJson(samples.get(i)));
            if (i + 1 < samples.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String sampleJson(Sample s) {
        return "{\"elapsedMs\":" + s.elapsedMs()
                + ",\"submitted\":" + s.submitted()
                + ",\"processed\":" + s.processed()
                + ",\"pending\":" + s.pending()
                + ",\"liveTokens\":" + s.liveTokens()
                + ",\"streamKeys\":" + s.streamKeys()
                + ",\"leaseMap\":" + s.leaseMap()
                + ",\"metricKeys\":" + s.metricKeys()
                + ",\"threads\":" + s.threads()
                + ",\"heapUsedMb\":" + s.heapUsedMb()
                + ",\"heapMaxMb\":" + s.heapMaxMb()
                + ",\"retried\":" + s.retried() + "}";
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static void writeHtml(Path path, List<Finding> findings, List<Sample> samples,
                                  Sample last, int bots, int seconds) throws IOException {
        boolean failed = findings.stream().anyMatch(f -> "FAIL".equals(f.severity()));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("<!DOCTYPE html><html><head><meta charset='utf-8'>");
            out.println("<title>YaPcore Endurance Report</title>");
            out.println("<style>");
            out.println("body{font-family:Segoe UI,system-ui,sans-serif;background:#161b22;color:#e6edf3;margin:24px}");
            out.println("h1{color:#2db58a} .FAIL{color:#e36b6b} .WARN{color:#e3b56b} .OK{color:#2db58a}");
            out.println("table{border-collapse:collapse;width:100%;margin:16px 0} td,th{border:1px solid #30363d;padding:8px;text-align:left}");
            out.println("code{background:#0d1117;padding:2px 6px;border-radius:4px}");
            out.println("</style></head><body>");
            out.println("<h1>YaPcore Endurance Report</h1>");
            out.println("<p>Generated " + Instant.now() + " · bots=" + bots
                    + " · loadSeconds=" + seconds
                    + " · result=<span class='" + (failed ? "FAIL" : "OK") + "'>"
                    + (failed ? "FAIL" : "PASS") + "</span></p>");
            out.println("<h2>Findings (actionable)</h2><table><tr><th>Sev</th><th>Code</th><th>Detail</th><th>How to fix</th></tr>");
            for (Finding f : findings) {
                out.println("<tr><td class='" + f.severity() + "'>" + f.severity()
                        + "</td><td><code>" + f.code() + "</code></td><td>" + esc(f.detail())
                        + "</td><td>" + esc(f.fixHint()) + "</td></tr>");
            }
            out.println("</table>");
            out.println("<h2>Final snapshot</h2><pre>" + esc(last.toString()) + "</pre>");
            out.println("<h2>Samples</h2><table><tr>"
                    + "<th>t(s)</th><th>sub</th><th>proc</th><th>pend</th><th>live</th>"
                    + "<th>streams</th><th>leases</th><th>metrics</th><th>thr</th><th>heap</th><th>retry</th></tr>");
            for (Sample s : samples) {
                out.printf("<tr><td>%.1f</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d/%d</td><td>%d</td></tr>%n",
                        s.elapsedMs() / 1000.0, s.submitted(), s.processed(), s.pending(),
                        s.liveTokens(), s.streamKeys(), s.leaseMap(), s.metricKeys(),
                        s.threads(), s.heapUsedMb(), s.heapMaxMb(), s.retried());
            }
            out.println("</table>");
            out.println("<p>This report is for <b>months-long</b> uptime readiness. "
                    + "Extend load with <code>-Dyap.endurance.seconds=86400</code>. "
                    + "Gradle's <code>problems-report.html</code> is build deprecations only — not this.</p>");
            out.println("</body></html>");
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
