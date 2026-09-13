package com.yapcore.server;

import com.yapcore.config.BedrockModeApplier;
import com.yapcore.config.ServerConfig;
import com.yapcore.web.DashboardLinkSnapshot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional Geyser-Standalone sibling JVM for {@code bedrock-mode=geyser-backup}.
 * Remote = YaP Link JE {@code 127.0.0.1:25565}. Owns Bedrock UDP {@code :19132}.
 */
public final class GeyserProcessManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.GeyserProcess");

    private final Path rootDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final StringBuilder recent = new StringBuilder(32 * 1024);

    private Process process;
    private Thread logPump;

    public GeyserProcessManager(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public Path linkHome() {
        return DashboardLinkSnapshot.resolveHome(rootDir, config.getLinkEmbedHome());
    }

    public Path geyserHome() {
        return BedrockModeApplier.geyserHome(rootDir, linkHome());
    }

    public Path resolveJar() {
        return BedrockModeApplier.geyserJar(rootDir, linkHome());
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    public synchronized void startIfBackupMode() throws IOException {
        String mode = BedrockModeApplier.readMode(linkHome(), rootDir.resolve("config/server.properties"));
        if (!BedrockModeApplier.isGeyserBackup(mode)) {
            return;
        }
        start();
    }

    public synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }
        Path home = geyserHome();
        Files.createDirectories(home);
        Path jar = resolveJar();
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Geyser-Standalone.jar missing: " + jar
                    + " (bedrock-mode=geyser-backup). Download Standalone and place it there.");
        }
        ensureMinimalConfig(home);
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-Xms512M");
        cmd.add("-Xmx1024M");
        cmd.add("-jar");
        cmd.add(jar.getFileName().toString());
        cmd.add("--config");
        cmd.add("config.yml");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(home.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        running.set(true);
        logPump = new Thread(this::pumpLogs, "yap-geyser-log");
        logPump.setDaemon(true);
        logPump.start();
        LOG.info("Geyser-Standalone started (backup Bedrock) home=" + home);
        publish("Geyser-Standalone started (bedrock-mode=geyser-backup)\n");
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        running.set(false);
        process = null;
        publish("Geyser-Standalone stopped\n");
    }

    private void ensureMinimalConfig(Path home) throws IOException {
        Path cfg = home.resolve("config.yml");
        if (Files.isRegularFile(cfg)) {
            return;
        }
        String yaml = """
                bedrock:
                  address: 0.0.0.0
                  port: 19132
                  clone-remote-port: false
                remote:
                  address: 127.0.0.1
                  port: 25565
                  auth-type: floodgate
                floodgate-key-file: ../floodgate-key.pem
                motd:
                  primary-motd: YaP
                  secondary-motd: Bedrock via Geyser backup
                """;
        Files.writeString(cfg, yaml);
    }

    private void pumpLogs() {
        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                if (n > 0) {
                    publish(new String(buf, 0, n));
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "geyser log pump", e);
        } finally {
            running.set(false);
        }
    }

    private void publish(String text) {
        synchronized (this) {
            recent.append(text);
            if (recent.length() > 200_000) {
                recent.delete(0, recent.length() - 100_000);
            }
        }
        for (Consumer<String> l : logListeners) {
            try {
                l.accept(text);
            } catch (Exception ignored) {
                // ignore listener errors
            }
        }
    }
}
