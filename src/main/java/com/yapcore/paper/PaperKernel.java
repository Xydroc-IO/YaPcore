package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import com.yapcore.paper.phase3.Phase3PaperRuntime;
import com.yaplabs.yapengine.YapEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper game authority for YaPcore.
 * <ul>
 *   <li>Phase 3 ({@code paper-phase3-tick-bridge=true} + embed): same-JVM Paperclip
 *       with YapEngine parent CL + spatial tick plugin</li>
 *   <li>Phase 2 embed: managed Paper process owns public JE port</li>
 *   <li>Phase 1: loopback Paper + TCP proxy</li>
 * </ul>
 */
public final class PaperKernel {

    private static final Logger LOG = Logger.getLogger("YaPcore.Paper");

    private final Path rootDir;
    private final ServerConfig config;
    private final YapEngine yapEngine;
    private Phase3PaperRuntime phase3;
    private final AtomicBoolean processRunning = new AtomicBoolean(false);
    private Process process;
    private Thread logPump;
    private Writer processStdin;
    private boolean usingPhase3;

    public PaperKernel(Path rootDir, ServerConfig config, YapEngine yapEngine) {
        this.rootDir = rootDir;
        this.config = config;
        this.yapEngine = yapEngine;
    }

    public Path paperDir() {
        return rootDir.resolve(config.getPaperDir()).toAbsolutePath().normalize();
    }

    public int listenPort() {
        return config.paperListenPort();
    }

    public boolean isEmbedded() {
        return config.isPaperEmbed();
    }

    public boolean isPhase3() {
        return usingPhase3;
    }

    public Phase3PaperRuntime phase3() {
        return phase3;
    }

    public boolean isRunning() {
        if (usingPhase3 && phase3 != null) {
            return phase3.isRunning();
        }
        return processRunning.get() && process != null && process.isAlive();
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!config.isPaperAuthority()) {
            LOG.info("Paper authority off (game-authority≠paper)");
            return;
        }
        if (Runtime.version().feature() < 25) {
            throw new IOException("Paper 26.2 requires Java 25+ (running " + Runtime.version() + ")");
        }

        if (config.isPaperEmbed() && config.isPaperPhase3TickBridge()) {
            Path cwd = Path.of("").toAbsolutePath().normalize();
            Path dir = paperDir();
            if (cwd.equals(dir)) {
                LOG.warning("Phase 3 spatial tick is retired as product path — "
                        + "prefer game-authority=folia. Starting legacy Phase 3 only because "
                        + "paper-phase3-tick-bridge=true.");
                usingPhase3 = true;
                phase3 = new Phase3PaperRuntime(rootDir, config, yapEngine);
                phase3.start();
                return;
            }
            LOG.warning("Phase 3 requested but cwd=" + cwd + " ≠ paper-dir=" + dir
                    + " — falling back to Phase 2 managed Paper. "
                    + "scripts/start.sh should cd into paper-kernel for Phase 3.");
        }

        usingPhase3 = false;
        if (config.isPaperEmbed()) {
            startManagedPublic();
        } else {
            startWrapProxy();
        }
    }

    public synchronized void stop() {
        if (usingPhase3 && phase3 != null) {
            phase3.stop();
            phase3 = null;
            usingPhase3 = false;
            return;
        }
        stopProcess();
    }

    private void startManagedPublic() throws IOException, InterruptedException {
        startProcess(listenPort(), bindIpForPublic(),
                "YaPcore Phase 2 managed Paper — owns public JE :" + listenPort());
        LOG.info("Phase 2 managed Paper online on :" + listenPort());
    }

    private void startWrapProxy() throws IOException, InterruptedException {
        startProcess(config.getPaperPort(), "127.0.0.1",
                "YaPcore Phase 1 Paper wrap — loopback proxy :" + config.getPort());
        LOG.info("Phase 1 Paper wrap online on 127.0.0.1:" + config.getPaperPort());
    }

    private String bindIpForPublic() {
        String bind = config.getBindHost();
        if (bind == null || bind.isBlank() || "0.0.0.0".equals(bind)) {
            return "";
        }
        return bind;
    }

    private void startProcess(int port, String bindIp, String propsComment)
            throws IOException, InterruptedException {
        if (!processRunning.compareAndSet(false, true)) {
            return;
        }
        Path dir = paperDir();
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("yap-paper-ready.marker"));
        PaperPluginsLayout.ensureUnifiedAndCompat(rootDir, dir, config);
        Path jar = PaperFiles.ensurePaperJar(rootDir, dir, config);
        PaperFiles.writeEula(dir);
        PaperFiles.writeServerProperties(rootDir, dir, config, port, bindIp, propsComment);
        PaperFiles.applyVelocitySupport(rootDir, dir, config);
        PaperOps.ensure(dir, config);
        List<String> cmd = buildCommand(jar);
        LOG.info("Starting Paper " + config.getPaperVersion() + " port=" + port + " dir=" + dir);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        processStdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        logPump = new Thread(this::pumpLogs, "yap-paper-log");
        logPump.setDaemon(true);
        logPump.start();
        if (!waitUntilReady(port, config.getPaperReadyTimeoutSec())) {
            stopProcess();
            throw new IOException("Paper did not become ready within "
                    + config.getPaperReadyTimeoutSec() + "s — check logs under " + dir);
        }
    }

    /**
     * Forward a console line to Paper (Phase 3 in-JVM or Phase 2 process stdin).
     * Players already use Paper's full command graph in-game; this is for YaP GUI/stdin.
     */
    public String dispatchConsoleCommand(String line) {
        if (!isRunning()) {
            return "Paper is not running";
        }
        if (usingPhase3 && phase3 != null) {
            return phase3.dispatchConsoleCommand(line);
        }
        if (process == null || !process.isAlive() || processStdin == null) {
            return "Paper process not accepting commands";
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
            return "Paper process: /" + cmd;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Paper stdin command failed", e);
            return "Paper stdin error: " + e.getMessage();
        }
    }

    private void stopProcess() {
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
            LOG.info("Paper process stopped");
        }
    }

    private List<String> buildCommand(Path jar) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        int ram = Math.max(512, config.getRamMb() / 2);
        cmd.add("-Xms" + Math.min(512, ram) + "M");
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("--enable-native-access=ALL-UNNAMED");
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());
        cmd.add("--nogui");
        return cmd;
    }

    private boolean waitUntilReady(int port, int timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(30, timeoutSec));
        Path marker = paperDir().resolve("yap-paper-ready.marker");
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
                LOG.info("[paper] " + line);
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(paperDir().resolve("yap-paper-ready.marker"),
                                "ready\n", StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "ready marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (processRunning.get()) {
                LOG.log(Level.WARNING, "Paper log pump ended: " + e.getMessage());
            }
        }
    }
}
