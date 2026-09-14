package com.yapcore.fleet.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Folia JVM for one fleet instance (same lifecycle contract as {@code FoliaProcess}).
 */
public final class InstanceProcess {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Process");
    private static final int LOG_RING = 2_000;

    private final String instanceId;
    private final Path instanceDir;
    private final AtomicBoolean processRunning = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<String> recentLogs = new ArrayDeque<>();
    private Process process;
    private Thread logPump;
    private Writer processStdin;

    public InstanceProcess(String instanceId, Path instanceDir) {
        this.instanceId = instanceId;
        this.instanceDir = instanceDir;
    }

    public String instanceId() {
        return instanceId;
    }

    public Path instanceDir() {
        return instanceDir;
    }

    public boolean isRunning() {
        return processRunning.get() && process != null && process.isAlive();
    }

    public void addLogListener(Consumer<String> listener) {
        if (listener != null) {
            logListeners.add(listener);
        }
    }

    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    public synchronized String recentLogText() {
        return String.join("", recentLogs);
    }

    public void start(List<String> command, int listenPort, int readyTimeoutSec)
            throws IOException, InterruptedException {
        if (!processRunning.compareAndSet(false, true)) {
            return;
        }
        Files.deleteIfExists(instanceDir.resolve("yap-folia-ready.marker"));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(instanceDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        processStdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        process.onExit().thenAccept(p -> {
            processRunning.set(false);
            LOG.warning("Fleet instance " + instanceId + " exited (code=" + p.exitValue() + ")");
        });
        logPump = new Thread(this::pumpLogs, "yap-fleet-" + instanceId + "-log");
        logPump.setDaemon(true);
        logPump.start();
        if (!waitUntilReady(listenPort, readyTimeoutSec)) {
            stop();
            throw new IOException("Fleet instance " + instanceId + " not ready within "
                    + readyTimeoutSec + "s — check logs under " + instanceDir);
        }
    }

    public String dispatchConsoleCommand(String line) {
        if (!isRunning()) {
            return "Instance " + instanceId + " is not running";
        }
        if (process == null || !process.isAlive() || processStdin == null) {
            return "Instance " + instanceId + " not accepting commands";
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
            return "Instance " + instanceId + ": /" + cmd;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "stdin failed for " + instanceId, e);
            return "stdin error: " + e.getMessage();
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
            LOG.info("Fleet instance " + instanceId + " stopped");
        }
    }

    private boolean waitUntilReady(int port, int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        Path marker = instanceDir.resolve("yap-folia-ready.marker");
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
                String tagged = "[fleet:" + instanceId + "] " + line + "\n";
                LOG.info(tagged.trim());
                appendRecent(tagged);
                for (Consumer<String> listener : logListeners) {
                    try {
                        listener.accept(tagged);
                    } catch (Exception ignored) {
                        // listener fault
                    }
                }
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(instanceDir.resolve("yap-folia-ready.marker"),
                                "ready\n", StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "ready marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (processRunning.get()) {
                LOG.log(Level.WARNING, "log pump ended for " + instanceId + ": " + e.getMessage());
            }
        }
    }

    private synchronized void appendRecent(String line) {
        recentLogs.addLast(line);
        while (recentLogs.size() > LOG_RING) {
            recentLogs.removeFirst();
        }
    }

    public List<String> drainRecentLines() {
        synchronized (this) {
            return new ArrayList<>(recentLogs);
        }
    }
}
