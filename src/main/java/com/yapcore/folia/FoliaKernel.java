package com.yapcore.folia;

import com.yapcore.config.ServerConfig;
import com.yapcore.paper.PaperOps;
import com.yapcore.paper.PaperPluginsLayout;

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
 * Folia game authority — managed Folia process owns the JE game port
 * (same layout as Paper Phase 2; no Phase 3 spatial tick).
 */
public final class FoliaKernel {

    private static final Logger LOG = Logger.getLogger("YaPcore.Folia");

    private final Path rootDir;
    private final ServerConfig config;
    private final AtomicBoolean processRunning = new AtomicBoolean(false);
    private Process process;
    private Thread logPump;
    private Writer processStdin;

    public FoliaKernel(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public Path foliaDir() {
        return rootDir.resolve(config.getFoliaDir()).toAbsolutePath().normalize();
    }

    public int listenPort() {
        return config.foliaListenPort();
    }

    public boolean isEmbedded() {
        return config.isFoliaEmbed();
    }

    public boolean isRunning() {
        return processRunning.get() && process != null && process.isAlive();
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!config.isFoliaAuthority()) {
            LOG.info("Folia authority off (game-authority≠folia)");
            return;
        }
        if (Runtime.version().feature() < 25) {
            throw new IOException("Folia 26.2 requires Java 25+ (running " + Runtime.version() + ")");
        }
        if (config.isFoliaEmbed()) {
            startManagedPublic();
        } else {
            startWrapProxy();
        }
    }

    public synchronized void stop() {
        stopProcess();
    }

    private void startManagedPublic() throws IOException, InterruptedException {
        startProcess(listenPort(), bindIpForPublic(),
                "YaPcore managed Folia — owns public JE :" + listenPort());
        LOG.info("Managed Folia online on :" + listenPort());
    }

    private void startWrapProxy() throws IOException, InterruptedException {
        startProcess(config.getFoliaPort(), "127.0.0.1",
                "YaPcore Folia wrap — loopback proxy :" + config.getPort());
        LOG.info("Folia wrap online on 127.0.0.1:" + config.getFoliaPort());
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
        Path dir = foliaDir();
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("yap-folia-ready.marker"));
        // Unified plugins/ only — no Paper plugin-compat rewrite for Folia product path.
        PaperPluginsLayout.ensureUnified(rootDir, dir);
        Path jar = FoliaFiles.ensureFoliaJar(rootDir, dir, config);
        FoliaFiles.writeEula(dir);
        FoliaFiles.writeServerProperties(rootDir, dir, config, port, bindIp, propsComment);
        FoliaFiles.applyVelocitySupport(rootDir, dir, config);
        FoliaFiles.ensureFoliaYml(dir);
        PaperOps.ensure(dir, config);
        List<String> cmd = buildCommand(jar);
        LOG.info("Starting Folia " + config.getFoliaVersion() + " port=" + port + " dir=" + dir);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        processStdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        logPump = new Thread(this::pumpLogs, "yap-folia-log");
        logPump.setDaemon(true);
        logPump.start();
        if (!waitUntilReady(port, config.getFoliaReadyTimeoutSec())) {
            stopProcess();
            throw new IOException("Folia did not become ready within "
                    + config.getFoliaReadyTimeoutSec() + "s — check logs under " + dir);
        }
    }

    public String dispatchConsoleCommand(String line) {
        if (!isRunning()) {
            return "Folia is not running";
        }
        if (process == null || !process.isAlive() || processStdin == null) {
            return "Folia process not accepting commands";
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
            return "Folia process: /" + cmd;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Folia stdin command failed", e);
            return "Folia stdin error: " + e.getMessage();
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
            LOG.info("Folia process stopped");
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
        Path marker = foliaDir().resolve("yap-folia-ready.marker");
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
                LOG.info("[folia] " + line);
                if (line.contains("Done (") || line.contains("For help, type \"help\"")) {
                    try {
                        Files.writeString(foliaDir().resolve("yap-folia-ready.marker"),
                                "ready\n", StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "ready marker", e);
                    }
                }
            }
        } catch (IOException e) {
            if (processRunning.get()) {
                LOG.log(Level.WARNING, "Folia log pump ended: " + e.getMessage());
            }
        }
    }
}
