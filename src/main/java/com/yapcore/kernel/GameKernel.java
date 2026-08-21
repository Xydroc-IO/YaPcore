package com.yapcore.kernel;

import com.yapcore.config.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boots Mojang's dedicated server as YaPcore's complete game kernel (subprocess).
 * YapEngine keeps the public multi-threaded edge; this process owns the full vanilla game.
 */
public final class GameKernel {

    private static final Logger LOG = Logger.getLogger("YaPcore.GameKernel");

    /** Minecraft 26.2 dedicated server (official Mojang object). */
    public static final String DEFAULT_VERSION = "26.2";
    private static final String DEFAULT_SERVER_URL =
            "https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar";

    private final Path rootDir;
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process process;
    private Thread logPump;

    public GameKernel(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public Path kernelDir() {
        return rootDir.resolve(config.getGameKernelDir());
    }

    public int listenPort() {
        return config.getGameKernelPort();
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!config.isGameKernelEnabled()) {
            LOG.info("Game kernel disabled (game-kernel-enabled=false) — JE uses native YapEngine world");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Path dir = kernelDir();
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("yap-kernel-ready.marker"));
        ensureKernelPortFree();
        Path jar = ensureServerJar(dir);
        writeEula(dir);
        writeKernelProperties(dir);
        List<String> cmd = buildCommand(jar);
        LOG.info("Starting Mojang game kernel " + config.getGameKernelVersion()
                + " on 127.0.0.1:" + listenPort() + " dir=" + dir.toAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        logPump = new Thread(this::pumpLogs, "yap-game-kernel-log");
        logPump.setDaemon(true);
        logPump.start();
        if (!waitUntilReady(config.getGameKernelReadyTimeoutSec())) {
            stop();
            throw new IOException("Game kernel did not become ready within "
                    + config.getGameKernelReadyTimeoutSec() + "s — check logs under " + dir);
        }
        LOG.info("Game kernel online — full vanilla world/commands on 127.0.0.1:" + listenPort());
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false) && process == null) {
            return;
        }
        running.set(false);
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive()) {
                try (Writer w = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    w.write("stop\n");
                    w.flush();
                } catch (IOException ignored) {
                    // fall through to destroy
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
            process = null;
            LOG.info("Game kernel stopped");
        }
    }

    private List<String> buildCommand(Path jar) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        int ram = Math.max(512, config.getRamMb() / 2);
        cmd.add("-Xms" + Math.min(512, ram) + "M");
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());
        cmd.add("--nogui");
        return cmd;
    }

    private Path ensureServerJar(Path dir) throws IOException {
        String version = config.getGameKernelVersion();
        Path jar = dir.resolve("server-" + version + ".jar");
        if (Files.isRegularFile(jar) && Files.size(jar) > 1_000_000) {
            return jar;
        }
        Path cached = rootDir.resolve("lib").resolve("server-" + version + ".jar");
        if (Files.isRegularFile(cached) && Files.size(cached) > 1_000_000) {
            Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        }
        // Dev fallback: previously downloaded bundler
        Path tmp = Path.of("/tmp/server-26.2.jar");
        if ("26.2".equals(version) && Files.isRegularFile(tmp) && Files.size(tmp) > 1_000_000) {
            Files.createDirectories(cached.getParent());
            Files.copy(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Using cached Mojang server jar from /tmp/server-26.2.jar");
            return jar;
        }
        String url = config.getGameKernelJarUrl();
        if (url == null || url.isBlank()) {
            url = DEFAULT_SERVER_URL;
        }
        LOG.info("Downloading Mojang dedicated server " + version + " …");
        Files.createDirectories(cached.getParent());
        download(url, cached);
        Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    private static void download(String url, Path dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(300_000);
        conn.setInstanceFollowRedirects(true);
        try (var in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        if (Files.size(dest) < 1_000_000) {
            throw new IOException("Downloaded server jar looks too small: " + dest);
        }
    }

    private static void writeEula(Path dir) throws IOException {
        Path eula = dir.resolve("eula.txt");
        Files.writeString(eula, "eula=true\n", StandardCharsets.UTF_8);
    }

    private void writeKernelProperties(Path dir) throws IOException {
        Path file = dir.resolve("server.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        p.setProperty("server-port", Integer.toString(listenPort()));
        p.setProperty("server-ip", "127.0.0.1");
        p.setProperty("online-mode", Boolean.toString(config.isOnlineMode()));
        p.setProperty("max-players", Integer.toString(config.getMaxPlayers()));
        p.setProperty("view-distance", Integer.toString(config.getViewDistance()));
        p.setProperty("simulation-distance", Integer.toString(config.getViewDistance()));
        p.setProperty("motd", config.getMotd());
        p.setProperty("difficulty", p.getProperty("difficulty", "normal"));
        p.setProperty("gamemode", p.getProperty("gamemode", "survival"));
        p.setProperty("level-name", p.getProperty("level-name", "world"));
        p.setProperty("level-type", p.getProperty("level-type", "minecraft:normal"));
        p.setProperty("spawn-protection", "0");
        p.setProperty("enable-command-block", "true");
        p.setProperty("sync-chunk-writes", "true");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "YaPcore Mojang game kernel — loopback only; public join via YaPcore :"
                    + config.getPort());
        }
    }

    private void ensureKernelPortFree() {
        try (var s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", listenPort()), 200);
            LOG.warning("Port " + listenPort() + " already in use — attempting to clear stale kernel");
        } catch (IOException expected) {
            return; // free
        }
        // Best-effort: nothing we own; operator may need to free the port
    }

    private boolean waitUntilReady(int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        Path marker = kernelDir().resolve("yap-kernel-ready.marker");
        while (System.nanoTime() < deadline) {
            if (process == null || !process.isAlive()) {
                return false;
            }
            // Require "Done" log marker — do not trust port alone (stale binds / early listen)
            if (Files.isRegularFile(marker)) {
                try (var s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", listenPort()), 1000);
                    return true;
                } catch (IOException e) {
                    // marker without accept yet
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
                LOG.info("[kernel] " + line);
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(kernelDir().resolve("yap-kernel-ready.marker"),
                                "ready\n", StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "ready marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.log(Level.WARNING, "Game kernel log pump ended: " + e.getMessage());
            }
        }
    }
}
