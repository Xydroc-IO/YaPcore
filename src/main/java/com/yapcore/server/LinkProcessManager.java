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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages YaP Link as a separate JVM ({@code yap-link.jar}), like stock Velocity.
 */
public final class LinkProcessManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.LinkProcess");

    private final Path rootDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final StringBuilder recent = new StringBuilder(32 * 1024);
    private static final int MAX_RECENT_CHARS = 200_000;

    private Process process;
    private Thread logPump;
    private Writer processStdin;

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
        if (isRunning()) {
            publish("[Link] Already running (pid=" + process.pid() + ")\n");
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
        ensureForwardingSecret(home);
        ensureLinkProperties(home);

        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.toString());
        cmd.add("-Xms512M");
        cmd.add("-Xmx1G");
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
        logPump = new Thread(this::pumpLogs, "yap-link-log");
        logPump.setDaemon(true);
        logPump.start();
        publish("[Link] Started pid=" + process.pid() + " home=" + home + "\n");
        LOG.info("YaP Link process started pid=" + process.pid());
    }

    public synchronized void stop() {
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
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
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
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[512];
            StringBuilder line = new StringBuilder();
            int n;
            while (running.get() && process != null && process.isAlive() && (n = reader.read(buf)) >= 0) {
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
            if (running.get()) {
                publish("[Link] log stream closed: " + e.getMessage() + "\n");
            }
        } finally {
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

    private void ensureForwardingSecret(Path home) throws IOException {
        Path dest = home.resolve("forwarding.secret");
        if (Files.isRegularFile(dest)) {
            return;
        }
        String fileProp = config.getVelocitySecretFile();
        if (fileProp != null && !fileProp.isBlank()) {
            Path src = Path.of(fileProp);
            if (!src.isAbsolute()) {
                src = rootDir.resolve(fileProp);
            }
            if (Files.isRegularFile(src)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        Path rootSecret = rootDir.resolve("forwarding.secret");
        if (Files.isRegularFile(rootSecret)) {
            Files.copy(rootSecret, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureLinkProperties(Path home) throws IOException {
        Path props = home.resolve("link.properties");
        if (Files.isRegularFile(props)) {
            return;
        }
        int backendPort = config.getPort();
        String content = """
                # YaP Link — seeded by YaPcore GUI
                bind=0.0.0.0:25565
                motd=YaP Link
                max-players=500
                online-mode=false
                player-info-forwarding-mode=modern
                forwarding-secret-file=forwarding.secret
                servers.lobby=127.0.0.1:%d
                try=lobby
                enable-server-command=true
                plugins-enabled=true
                ping-passthrough=true
                chat-relay-enabled=true
                bedrock-enabled=false
                """.formatted(backendPort);
        Files.writeString(props, content);
    }
}
