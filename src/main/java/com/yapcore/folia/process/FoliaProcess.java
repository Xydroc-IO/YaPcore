package com.yapcore.folia.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed YaP-Folia JVM process lifecycle (stdin commands, log pump, ready wait).
 */
public final class FoliaProcess {

    private static final Logger LOG = Logger.getLogger("YaPcore.YaPFoliaProcess");

    private final Path foliaDir;
    private final AtomicBoolean processRunning = new AtomicBoolean(false);
    private Process process;
    private Thread logPump;
    private Writer processStdin;

    public FoliaProcess(Path foliaDir) {
        this.foliaDir = foliaDir;
    }

    public boolean isRunning() {
        return processRunning.get() && process != null && process.isAlive();
    }

    public void start(List<String> command, int listenPort, int readyTimeoutSec)
            throws IOException, InterruptedException {
        if (!processRunning.compareAndSet(false, true)) {
            return;
        }
        Files.deleteIfExists(foliaDir.resolve("yap-folia-ready.marker"));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(foliaDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        processStdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        process.onExit().thenAccept(p -> {
            processRunning.set(false);
            // MSPT benches shut YaP-Folia via Bukkit.shutdown(); chassis must follow or hang.
            if (System.getProperty("yap.bench.scenario") != null
                    || System.getProperty("yap.bench.out") != null) {
                LOG.info("YaP-Folia exited during bench (code=" + p.exitValue() + ") — exiting chassis");
                System.exit(p.exitValue() == 0 ? 0 : 1);
            } else {
                LOG.warning("YaP-Folia child exited unexpectedly (code=" + p.exitValue()
                        + ") — chassis stays up; restart YaP-Folia or stop the product");
            }
        });
        logPump = new Thread(this::pumpLogs, "yap-folia-log");
        logPump.setDaemon(true);
        logPump.start();
        if (!waitUntilReady(listenPort, readyTimeoutSec)) {
            stop();
            throw new IOException("YaP-Folia did not become ready within "
                    + readyTimeoutSec + "s — check logs under " + foliaDir);
        }
    }

    public String dispatchConsoleCommand(String line) {
        if (!isRunning()) {
            return "YaP-Folia is not running";
        }
        if (process == null || !process.isAlive() || processStdin == null) {
            return "YaP-Folia process not accepting commands";
        }
        try {
            String cmd = line == null ? "" : line.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            synchronized (processStdin) {
                processStdin.write(cmd);
                processStdin.write('\n');
                processStdin.flush();
            }
            return "YaP-Folia process: /" + cmd;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "YaP-Folia stdin command failed", e);
            return "YaP-Folia stdin error: " + e.getMessage();
        }
    }

    public void stop() {
        if (!processRunning.compareAndSet(true, false) && process == null) {
            return;
        }
        processRunning.set(false);
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive()) {
                try {
                    Writer w = processStdin != null ? processStdin
                            : new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
                    synchronized (w) {
                        w.write("stop\n");
                        w.flush();
                    }
                } catch (IOException ignored) {
                    // destroy
                }
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            processStdin = null;
            process = null;
            LOG.info("YaP-Folia process stopped");
        }
    }

    private boolean waitUntilReady(int port, int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        Path marker = foliaDir.resolve("yap-folia-ready.marker");
        while (System.nanoTime() < deadline) {
            if (process == null || !process.isAlive()) {
                return false;
            }
            if (Files.isRegularFile(marker)) {
                try (var s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000);
                    return true;
                } catch (IOException e) {
                    // wait
                }
            }
            Thread.sleep(250);
        }
        return false;
    }

    private void pumpLogs() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                LOG.info("[yap-folia] " + line);
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(foliaDir.resolve("yap-folia-ready.marker"),
                                "ready\n", StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "ready marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (processRunning.get()) {
                LOG.log(Level.WARNING, "YaP-Folia log pump ended: " + e.getMessage());
            }
        }
    }
}
