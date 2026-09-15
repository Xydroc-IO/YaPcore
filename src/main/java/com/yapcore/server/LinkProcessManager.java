package com.yapcore.server;

import com.yapcore.config.ServerConfig;
import com.yapcore.web.DashboardLinkSnapshot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages YaP Link as a separate JVM ({@code yap-link.jar}), like stock Velocity.
 */
public final class LinkProcessManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.LinkProcess");
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final long STABLE_RESET_MS = 30_000L;

    private final Path rootDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Operator/server wants Link up — cleared only by {@link #stop()}. */
    private final AtomicBoolean wantRunning = new AtomicBoolean(false);
    private final AtomicInteger restartAttempt = new AtomicInteger(0);
    private final StringBuilder recent = new StringBuilder(32 * 1024);
    private static final int MAX_RECENT_CHARS = 200_000;

    private Process process;
    private Thread logPump;
    private Writer processStdin;
    private volatile long startedAtMs;

    public LinkProcessManager(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public void addLogListener(Consumer<String> listener) {
        if (listener != null) {
            logListeners.add(listener);
        }
    }

    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    public synchronized String getRecentText() {
        return recent.toString();
    }

    /** Append operator-visible text (scripts, dashboard actions). */
    public void appendLog(String text) {
        publish(text);
    }

    public boolean isLinkEmbed() {
        return config.isLinkEmbed();
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    public Path linkHome() {
        return DashboardLinkSnapshot.resolveHome(rootDir, config.getLinkEmbedHome());
    }

    public Path resolveJar() {
        Path release = rootDir.resolve("yap-link.jar");
        if (Files.isRegularFile(release)) {
            return release.toAbsolutePath().normalize();
        }
        Path dev = rootDir.resolve("yap-first-party/link/native/build/libs/yap-link.jar");
        if (Files.isRegularFile(dev)) {
            return dev.toAbsolutePath().normalize();
        }
        return release;
    }

    public synchronized void start() throws IOException {
        if (config.isLinkEmbed()) {
            throw new IOException("link-embed=true — Link runs in-process at JVM boot. "
                    + "Set link-embed=false to control a separate Link process from the GUI.");
        }
        wantRunning.set(true);
        restartAttempt.set(0);
        if (isRunning()) {
            publish("[Link] Already running (pid=" + process.pid() + ")\n");
            return;
        }
        spawnLink();
    }

    private synchronized void spawnLink() throws IOException {
        if (isRunning()) {
            return;
        }
        Path jar = resolveJar();
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Missing yap-link.jar — run: gradle :yap-link-native:shadowJar "
                    + "or use assembleRelease");
        }
        Path home = linkHome();
        Files.createDirectories(home);
        Files.createDirectories(home.resolve("plugins"));
        LinkProcessHomeSetup.ensureForwardingSecret(rootDir, config, home);
        LinkProcessHomeSetup.ensureLinkProperties(config, home);
        // Manual start-yap-link.sh / force-killed parents leave orphans on :25565/:19132.
        reclaimOrphanLinkProcesses(home);

        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.toString());
        cmd.add("-Xms512M");
        cmd.add("-Xmx1G");
        // REAL LevelChunks kill-switch (translator default true; set false to force EMPTY).
        String realChunks = System.getProperty("yap.link.bedrock.realLevelChunks");
        if (realChunks != null && !realChunks.isBlank()) {
            cmd.add("-Dyap.link.bedrock.realLevelChunks=" + realChunks);
        }
        // Post-0x71 abilities (default ON in translator). Legacy postInitInteract still honored.
        String postInit = System.getProperty("yap.link.bedrock.postInitInteract");
        if (postInit != null && !postInit.isBlank()) {
            cmd.add("-Dyap.link.bedrock.postInitInteract=" + postInit);
        }
        String postInitAbilities = System.getProperty("yap.link.bedrock.postInitAbilities");
        if (postInitAbilities != null && !postInitAbilities.isBlank()) {
            cmd.add("-Dyap.link.bedrock.postInitAbilities=" + postInitAbilities);
        }
        // PlayerList ADD-self: Link jar defaults ON (Steve+geometry). Pass skip kill-switch
        // only when ops set it — do not pin postInitPlayerList=false (blocks tab).
        String skipPlayerList = System.getProperty("yap.link.bedrock.skipPlayerList");
        if (skipPlayerList != null && !skipPlayerList.isBlank()) {
            cmd.add("-Dyap.link.bedrock.skipPlayerList=" + skipPlayerList);
        }
        String postInitPlayerList = System.getProperty("yap.link.bedrock.postInitPlayerList");
        if (postInitPlayerList != null && !postInitPlayerList.isBlank()) {
            cmd.add("-Dyap.link.bedrock.postInitPlayerList=" + postInitPlayerList);
        }
        cmd.add("-jar");
        cmd.add(jar.toString());
        cmd.add("--home");
        cmd.add(home.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(home.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        processStdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        running.set(true);
        startedAtMs = System.currentTimeMillis();
        final Process spawned = process;
        spawned.onExit().thenAccept(this::onLinkExit);
        logPump = new Thread(this::pumpLogs, "yap-link-log");
        logPump.setDaemon(true);
        logPump.start();
        publish("[Link] Started pid=" + spawned.pid() + " home=" + home + "\n");
        LOG.info("YaP Link process started pid=" + spawned.pid());
    }

    /**
     * Kill other {@code yap-link.jar} JVMs for the same {@code --home} so auto-restart
     * is not stuck in {@code BindException} against a manual/orphan edge process.
     */
    private void reclaimOrphanLinkProcesses(Path home) {
        Path homeNorm = home.toAbsolutePath().normalize();
        String homeStr = homeNorm.toString();
        long selfPid = ProcessHandle.current().pid();
        long managedPid = process != null && process.isAlive() ? process.pid() : -1L;
        List<ProcessHandle> victims = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(ph -> {
            if (ph.pid() == selfPid || ph.pid() == managedPid) {
                return;
            }
            String cl = ph.info().commandLine().orElse("");
            if (cl.isBlank() || !cl.contains("yap-link")) {
                return;
            }
            if (!cl.contains("-jar") || !cl.contains("yap-link")) {
                return;
            }
            // Require same home (absolute or as trailing --home arg).
            if (!cl.contains(homeStr) && !cl.contains("--home " + homeStr)) {
                // Also accept relative link-data under this root.
                Path rel = rootDir.resolve("link-data").toAbsolutePath().normalize();
                if (!homeNorm.equals(rel) || !cl.contains(rel.toString())) {
                    return;
                }
            }
            victims.add(ph);
        });
        if (victims.isEmpty()) {
            return;
        }
        for (ProcessHandle ph : victims) {
            publish("[Link] Reclaiming orphan yap-link pid=" + ph.pid() + " (same home)\n");
            LOG.warning("Reclaiming orphan YaP Link pid=" + ph.pid() + " home=" + homeStr);
            ph.destroy();
        }
        long deadline = System.currentTimeMillis() + 2_000L;
        for (ProcessHandle ph : victims) {
            while (ph.isAlive() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (ph.isAlive()) {
                ph.destroyForcibly();
            }
        }
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onLinkExit(Process exited) {
        int code;
        try {
            code = exited.exitValue();
        } catch (IllegalThreadStateException e) {
            return;
        }
        synchronized (this) {
            if (process != exited) {
                return;
            }
            running.set(false);
            processStdin = null;
            process = null;
        }
        publish("[Link] Exited code=" + code + "\n");
        LOG.warning("YaP Link exited code=" + code);
        if (!wantRunning.get()) {
            return;
        }
        long livedMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        if (livedMs >= STABLE_RESET_MS) {
            restartAttempt.set(0);
        }
        int attempt = restartAttempt.getAndIncrement();
        long delay = Math.min(MAX_BACKOFF_MS, 1_000L << Math.min(attempt, 5));
        publish("[Link] Auto-restart in " + delay + "ms (attempt=" + (attempt + 1) + ")\n");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!wantRunning.get()) {
                    return;
                }
                synchronized (this) {
                    if (!wantRunning.get() || isRunning()) {
                        return;
                    }
                    try {
                        spawnLink();
                        publish("[Link] Auto-restarted\n");
                    } catch (IOException e) {
                        publish("[Link] Auto-restart failed: " + e.getMessage() + "\n");
                        LOG.log(Level.WARNING, "YaP Link auto-restart failed", e);
                        // Re-arm backoff even when spawn throws before a child exists.
                        if (wantRunning.get()) {
                            onLinkExitFailed();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "yap-link-autorestart");
        t.setDaemon(true);
        t.start();
    }

    /** Schedule another retry when spawn itself throws (no Process.onExit). */
    private void onLinkExitFailed() {
        if (!wantRunning.get()) {
            return;
        }
        int attempt = restartAttempt.getAndIncrement();
        long delay = Math.min(MAX_BACKOFF_MS, 1_000L << Math.min(attempt, 5));
        publish("[Link] Auto-restart retry in " + delay + "ms (attempt=" + (attempt + 1) + ")\n");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!wantRunning.get()) {
                    return;
                }
                synchronized (this) {
                    if (!wantRunning.get() || isRunning()) {
                        return;
                    }
                    try {
                        spawnLink();
                        publish("[Link] Auto-restarted\n");
                    } catch (IOException e) {
                        publish("[Link] Auto-restart failed: " + e.getMessage() + "\n");
                        if (wantRunning.get()) {
                            onLinkExitFailed();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "yap-link-autorestart");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        wantRunning.set(false);
        restartAttempt.set(0);
        if (!running.get() && process == null) {
            return;
        }
        running.set(false);
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
                // Graceful stop should exit quickly; do not block the GUI for 30s.
                if (!process.waitFor(4, TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(2, TimeUnit.SECONDS);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            processStdin = null;
            process = null;
            publish("[Link] Stopped\n");
        }
    }

    public String dispatchCommand(String line) {
        if (!isRunning()) {
            return "YaP Link is not running";
        }
        if (processStdin == null) {
            return "Link process not accepting commands";
        }
        try {
            String cmd = line == null ? "" : line.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (cmd.isEmpty()) {
                return "";
            }
            synchronized (processStdin) {
                processStdin.write(cmd);
                processStdin.write('\n');
                processStdin.flush();
            }
            publish("> " + cmd + "\n");
            return "Link: " + cmd;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Link stdin command failed", e);
            return "Link stdin error: " + e.getMessage();
        }
    }

    private void pumpLogs() {
        Process p = process;
        if (p == null) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[512];
            StringBuilder line = new StringBuilder();
            int n;
            while ((n = reader.read(buf)) >= 0) {
                for (int i = 0; i < n; i++) {
                    char c = buf[i];
                    if (c == '\n') {
                        publish(line + "\n");
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append(c);
                    }
                }
            }
            if (!line.isEmpty()) {
                publish(line + "\n");
            }
        } catch (IOException e) {
            if (wantRunning.get() || running.get()) {
                publish("[Link] log stream closed: " + e.getMessage() + "\n");
            }
        }
        // Do not clear wantRunning here — onExit owns lifecycle / auto-restart.
        if (process == p) {
            running.set(false);
        }
    }

    private void publish(String text) {
        String stamped = text.endsWith("\n") ? text : text + "\n";
        synchronized (recent) {
            recent.append(stamped);
            if (recent.length() > MAX_RECENT_CHARS) {
                recent.delete(0, recent.length() - MAX_RECENT_CHARS);
            }
        }
        for (Consumer<String> listener : logListeners) {
            try {
                listener.accept(stamped);
            } catch (Exception ignored) {
            }
        }
    }
}
