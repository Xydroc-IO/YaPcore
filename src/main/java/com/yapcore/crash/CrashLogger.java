package com.yapcore.crash;

import com.yapcore.console.ConsoleBus;
import com.yapcore.util.ThreadMetrics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extensive diagnostic crash / fault logger.
 * Writes everything needed to reproduce and fix issues: threads, heap, config,
 * plugins, recent console, metrics, and full stack traces.
 */
public final class CrashLogger {

    private static final Logger LOG = Logger.getLogger("YaPcore.Crash");
    private static final CrashLogger INSTANCE = new CrashLogger();
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final AtomicReference<Path> crashDir = new AtomicReference<>(Path.of("logs", "crashes"));
    private final AtomicReference<Supplier<Map<String, String>>> contextSupplier =
            new AtomicReference<>(LinkedHashMap::new);

    private CrashLogger() {
    }

    public static CrashLogger get() {
        return INSTANCE;
    }

    public void configure(Path crashDirectory, Supplier<Map<String, String>> contextSupplier) {
        if (crashDirectory != null) {
            crashDir.set(crashDirectory);
        }
        if (contextSupplier != null) {
            this.contextSupplier.set(contextSupplier);
        }
    }

    public void installGlobalHandlers() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOG.log(Level.SEVERE, "Uncaught exception on " + thread.getName(), throwable);
            dump("uncaught-" + sanitize(thread.getName()), throwable, Map.of(
                    "thread", thread.getName(),
                    "thread-id", Long.toString(thread.threadId()),
                    "thread-state", String.valueOf(thread.getState())
            ));
        });
    }

    public Path logPluginFault(String plugin, String phase, Throwable error) {
        return dump("plugin-" + sanitize(plugin) + "-" + sanitize(phase), error, Map.of(
                "plugin", plugin,
                "phase", phase
        ));
    }

    public Path logWatchdogRecovery(String reason, Map<String, String> extra) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("reason", reason);
        if (extra != null) {
            meta.putAll(extra);
        }
        return dump("watchdog-recovery", null, meta);
    }

    public Path dump(String kind, Throwable error) {
        return dump(kind, error, Map.of());
    }

    public Path dump(String kind, Throwable error, Map<String, String> extra) {
        try {
            Path dir = crashDir.get();
            Files.createDirectories(dir);
            String stamp = FILE_TS.format(Instant.now());
            Path file = dir.resolve("crash-" + stamp + "-" + sanitize(kind) + ".log");
            String report = buildReport(kind, error, extra);
            Files.writeString(file, report, StandardCharsets.UTF_8);
            // Also keep a latest pointer
            Files.writeString(dir.resolve("latest.log"), report, StandardCharsets.UTF_8);
            LOG.severe("Crash report written to " + file.toAbsolutePath());
            ConsoleBus.get().publish("[CRASH] Report saved: " + file.getFileName());
            return file;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to write crash report", e);
            return null;
        }
    }

    public String buildReport(String kind, Throwable error, Map<String, String> extra) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("==== YaPcore Crash / Diagnostic Report ====\n");
        sb.append("timestamp: ").append(Instant.now()).append('\n');
        sb.append("kind: ").append(kind).append('\n');
        sb.append('\n');

        sb.append("--- Extra metadata ---\n");
        if (extra != null) {
            extra.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        }
        sb.append('\n');

        sb.append("--- Server context ---\n");
        try {
            Map<String, String> ctx = contextSupplier.get().get();
            if (ctx != null) {
                ctx.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
            }
        } catch (Exception e) {
            sb.append("context-error: ").append(e.getMessage()).append('\n');
        }
        sb.append('\n');

        appendJvm(sb);
        appendMemory(sb);
        appendThreads(sb);
        appendMetrics(sb);
        appendConsole(sb);

        if (error != null) {
            sb.append("--- Primary exception ---\n");
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
            sb.append('\n');
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root != error) {
                sb.append("--- Root cause ---\n");
                StringWriter sw2 = new StringWriter();
                root.printStackTrace(new PrintWriter(sw2));
                sb.append(sw2).append('\n');
            }
        }

        sb.append("==== End of report ====\n");
        return sb.toString();
    }

    private static void appendJvm(StringBuilder sb) {
        sb.append("--- JVM / OS ---\n");
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        sb.append("java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("java.home: ").append(System.getProperty("java.home")).append('\n');
        sb.append("vm: ").append(rt.getVmName()).append(' ').append(rt.getVmVersion()).append('\n');
        sb.append("os: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(' ')
                .append(System.getProperty("os.arch")).append('\n');
        sb.append("uptime-ms: ").append(rt.getUptime()).append('\n');
        sb.append("pid: ").append(ProcessHandle.current().pid()).append('\n');
        sb.append("cwd: ").append(Path.of("").toAbsolutePath()).append('\n');
        sb.append("input-args: ").append(rt.getInputArguments()).append('\n');
        sb.append('\n');
    }

    private static void appendMemory(StringBuilder sb) {
        sb.append("--- Memory ---\n");
        Runtime rt = Runtime.getRuntime();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        sb.append("heap-used-mb: ").append((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).append('\n');
        sb.append("heap-total-mb: ").append(rt.totalMemory() / (1024 * 1024)).append('\n');
        sb.append("heap-max-mb: ").append(rt.maxMemory() / (1024 * 1024)).append('\n');
        sb.append("heap-mx: ").append(mem.getHeapMemoryUsage()).append('\n');
        sb.append("nonheap-mx: ").append(mem.getNonHeapMemoryUsage()).append('\n');
        sb.append("processors: ").append(rt.availableProcessors()).append('\n');
        sb.append('\n');
    }

    private static void appendThreads(StringBuilder sb) {
        sb.append("--- All threads (full stacks) ---\n");
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] ids = threads.getAllThreadIds();
        ThreadInfo[] infos = threads.getThreadInfo(ids, true, true);
        if (infos == null) {
            infos = threads.getThreadInfo(ids, Integer.MAX_VALUE);
        }
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            sb.append('"').append(info.getThreadName()).append("\" #")
                    .append(info.getThreadId())
                    .append(" state=").append(info.getThreadState());
            if (info.getLockName() != null) {
                sb.append(" on ").append(info.getLockName());
            }
            if (info.getLockOwnerName() != null) {
                sb.append(" owned by ").append(info.getLockOwnerName())
                        .append(" id=").append(info.getLockOwnerId());
            }
            sb.append('\n');
            StackTraceElement[] stack = info.getStackTrace();
            for (StackTraceElement el : stack) {
                sb.append("    at ").append(el).append('\n');
            }
            sb.append('\n');
        }
        long[] deadlocks = threads.findDeadlockedThreads();
        if (deadlocks != null && deadlocks.length > 0) {
            sb.append("!!! DEADLOCK DETECTED ids=").append(java.util.Arrays.toString(deadlocks)).append('\n');
        }
        sb.append('\n');
    }

    private static void appendMetrics(StringBuilder sb) {
        sb.append("--- ThreadMetrics snapshot ---\n");
        ThreadMetrics.snapshot().forEach((k, v) ->
                sb.append(k).append(" = ").append(v).append('\n'));
        sb.append('\n');
    }

    private static void appendConsole(StringBuilder sb) {
        sb.append("--- Recent console (tail) ---\n");
        String recent = ConsoleBus.get().getRecentText();
        if (recent.length() > 80_000) {
            recent = recent.substring(recent.length() - 80_000);
        }
        sb.append(recent);
        if (!recent.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append('\n');
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
