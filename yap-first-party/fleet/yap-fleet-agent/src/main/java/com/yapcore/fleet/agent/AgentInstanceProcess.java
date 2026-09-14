package com.yapcore.fleet.agent;

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
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Folia process managed by the fleet agent (independent of chassis FoliaProcess). */
final class AgentInstanceProcess {

    private static final Logger LOG = Logger.getLogger("YaP.FleetAgent.Proc");

    private final String id;
    private final Path dir;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private Process process;
    private Writer stdin;

    AgentInstanceProcess(String id, Path dir) {
        this.id = id;
        this.dir = dir;
    }

    boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    void addLogListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    synchronized String recentLogs() {
        return String.join("", recent);
    }

    void start(Path jar, int port) throws IOException, InterruptedException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Files.deleteIfExists(dir.resolve("yap-folia-ready.marker"));
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-Xms512M");
        cmd.add("-Xmx2G");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());
        cmd.add("--nogui");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        process.onExit().thenAccept(p -> running.set(false));
        Thread pump = new Thread(this::pump, "yap-agent-" + id + "-log");
        pump.setDaemon(true);
        pump.start();
        if (!waitReady(port, 180)) {
            stop();
            throw new IOException("Instance " + id + " not ready");
        }
    }

    String dispatch(String line) {
        if (!isRunning() || stdin == null) {
            return "not running";
        }
        try {
            String cmd = line == null ? "" : line.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            synchronized (stdin) {
                stdin.write(cmd);
                stdin.write('\n');
                stdin.flush();
            }
            return "ok: /" + cmd;
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }

    void stop() {
        running.set(false);
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive() && stdin != null) {
                synchronized (stdin) {
                    stdin.write("stop\n");
                    stdin.flush();
                }
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            process.destroyForcibly();
        } finally {
            stdin = null;
            process = null;
        }
    }

    private boolean waitReady(int port, int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
        Path marker = dir.resolve("yap-folia-ready.marker");
        while (System.nanoTime() < deadline) {
            if (process == null || !process.isAlive()) {
                return false;
            }
            if (Files.isRegularFile(marker)) {
                try (var s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000);
                    return true;
                } catch (IOException ignored) {
                }
            }
            Thread.sleep(250);
        }
        return false;
    }

    private void pump() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String tagged = "[agent:" + id + "] " + line + "\n";
                synchronized (this) {
                    recent.addLast(tagged);
                    while (recent.size() > 1500) {
                        recent.removeFirst();
                    }
                }
                for (Consumer<String> l : listeners) {
                    try {
                        l.accept(tagged);
                    } catch (Exception ignored) {
                    }
                }
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(dir.resolve("yap-folia-ready.marker"), "ready\n");
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.warning("log pump ended: " + e.getMessage());
            }
        }
    }

    static Path findJar(Path instanceDir, Path home) throws IOException {
        try (Stream<Path> s = Files.list(instanceDir)) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("folia-") && n.endsWith(".jar") && Files.size(p) > 1_000_000) {
                    return p;
                }
            }
        }
        Path lib = home.resolve("lib");
        if (Files.isDirectory(lib)) {
            try (Stream<Path> s = Files.list(lib)) {
                for (Path p : s.toList()) {
                    String n = p.getFileName().toString();
                    if ((n.startsWith("folia-") || n.startsWith("yap-folia-"))
                            && n.endsWith(".jar") && Files.size(p) > 1_000_000) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    static int readPort(Path dir, int fallback) {
        Path props = dir.resolve("server.properties");
        if (!Files.isRegularFile(props)) {
            return fallback;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(props)) {
            p.load(in);
            return Integer.parseInt(p.getProperty("server-port", String.valueOf(fallback)).trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
