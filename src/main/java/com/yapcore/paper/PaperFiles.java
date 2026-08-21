package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import com.yapcore.fill.FillClient;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/** Shared Paper jar / eula / server.properties helpers for wrap + embed. */
public final class PaperFiles {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperFiles");

    private PaperFiles() {
    }

    public static Path ensurePaperJar(Path rootDir, Path paperDir, ServerConfig config) throws IOException {
        String version = config.getPaperVersion();
        Files.createDirectories(paperDir);

        // Prefer YaPcore-built Paperclip from vendor/paper (Phase 3)
        Path yapCached = rootDir.resolve("lib").resolve("paper-" + version + "-yap.jar");
        Path jar = paperDir.resolve("paper-" + version + ".jar");
        if (Files.isRegularFile(yapCached) && Files.size(yapCached) > 1_000_000) {
            Files.copy(yapCached, jar, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Using vendored YaP Paperclip → " + yapCached.getFileName());
            return jar;
        }
        if (config.isPaperPhase3NmsTick() && config.isPaperPhase3TickBridge()
                && config.isPaperEmbed() && config.isPaperAuthority()) {
            throw new IOException("Missing YaP Paperclip " + yapCached
                    + " — required when paper-phase3-nms-tick=true. "
                    + "Run ./scripts/build-vendor-paper.sh (or disable NMS tick)");
        }

        if (Files.isRegularFile(jar) && Files.size(jar) > 1_000_000) {
            return jar;
        }
        Path cached = rootDir.resolve("lib").resolve("paper-" + version + ".jar");
        if (Files.isRegularFile(cached) && Files.size(cached) > 1_000_000) {
            Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        }
        String url = config.getPaperJarUrl();
        if (url == null || url.isBlank()) {
            url = FillClient.latestServerJarUrl("paper", version);
        }
        Files.createDirectories(cached.getParent());
        download(url, cached);
        Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    public static void download(String url, Path dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "YaPcore/PaperPort");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(300_000);
        conn.setInstanceFollowRedirects(true);
        try (var in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        if (Files.size(dest) < 1_000_000) {
            throw new IOException("Downloaded Paper jar looks too small: " + dest);
        }
    }

    public static void writeEula(Path dir) throws IOException {
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n", StandardCharsets.UTF_8);
    }

    public static void writeServerProperties(Path dir, ServerConfig config, int listenPort, String bindIp, String comment)
            throws IOException {
        Path file = dir.resolve("server.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        String effectiveBind = effectivePaperBind(config, bindIp);
        p.setProperty("server-port", Integer.toString(listenPort));
        p.setProperty("server-ip", effectiveBind == null ? "" : effectiveBind);
        // Velocity authenticates; Paper must be offline-mode when modern forwarding is on.
        boolean online = config.isVelocityEnabled() ? false : config.isOnlineMode();
        p.setProperty("online-mode", Boolean.toString(online));
        if (config.isVelocityEnabled()) {
            p.setProperty("prevent-proxy-connections", "false");
        }
        p.setProperty("max-players", Integer.toString(config.getMaxPlayers()));
        p.setProperty("view-distance", Integer.toString(config.getViewDistance()));
        p.setProperty("simulation-distance", Integer.toString(config.getViewDistance()));
        p.setProperty("motd", config.getMotd());
        p.setProperty("spawn-protection", "0");
        p.setProperty("enable-command-block", "true");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, comment);
        }
    }

    /**
     * When Velocity is enabled and {@code velocity-bind-localhost=true}, force Paper
     * onto loopback so the public edge is the proxy only.
     */
    public static String effectivePaperBind(ServerConfig config, String requestedBind) {
        if (config.isVelocityEnabled() && config.isVelocityBindLocalhost()) {
            return "127.0.0.1";
        }
        if (requestedBind == null || requestedBind.isBlank() || "0.0.0.0".equals(requestedBind)) {
            return "";
        }
        return requestedBind;
    }

    /**
     * Sync Paper for Velocity modern forwarding: {@code paper-global.yml},
     * {@code spigot.yml} ({@code bungeecord: false}), and log the mode.
     * Only runs when {@code velocity-enabled=true}; does not rewrite Paper configs
     * when Velocity mode is off.
     *
     * @param rootDir repo / process root (for resolving {@code velocity-secret-file})
     * @param paperDir Paper working directory ({@code paper-kernel} by default)
     */
    public static void applyVelocitySupport(Path rootDir, Path paperDir, ServerConfig config)
            throws IOException {
        if (!config.isVelocityEnabled()) {
            return;
        }
        String secret = resolveVelocitySecret(rootDir, config);
        if (secret.isBlank()) {
            throw new IOException("velocity-enabled=true but no secret set — "
                    + "set velocity-secret or velocity-secret-file (must match Velocity forwarding.secret)");
        }
        writePaperVelocityGlobal(paperDir, true, config.isVelocityOnlineMode(), secret);
        ensureSpigotBungeeOff(paperDir);
        LOG.info("Velocity modern forwarding enabled for Paper (online-mode="
                + config.isVelocityOnlineMode()
                + ", bind="
                + (config.isVelocityBindLocalhost() ? "127.0.0.1" : "config bind")
                + ") — players must join via Velocity");
    }

    static String resolveVelocitySecret(Path rootDir, ServerConfig config) throws IOException {
        String file = config.getVelocitySecretFile();
        if (file != null && !file.isBlank()) {
            Path path = Path.of(file);
            if (!path.isAbsolute()) {
                path = rootDir.resolve(path);
            }
            if (!Files.isRegularFile(path)) {
                throw new IOException("velocity-secret-file not found: " + path);
            }
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        }
        return config.getVelocitySecret() == null ? "" : config.getVelocitySecret().trim();
    }

    @SuppressWarnings("unchecked")
    private static void writePaperVelocityGlobal(Path paperDir, boolean enabled,
                                                 boolean velocityOnlineMode, String secret)
            throws IOException {
        Path cfgDir = paperDir.resolve("config");
        Files.createDirectories(cfgDir);
        Path global = cfgDir.resolve("paper-global.yml");
        Yaml yaml = paperYaml();
        Map<String, Object> root;
        if (Files.isRegularFile(global)) {
            try (InputStream in = Files.newInputStream(global)) {
                Object loaded = yaml.load(in);
                root = loaded instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m)
                        : new LinkedHashMap<>();
            }
        } else {
            root = new LinkedHashMap<>();
        }
        Map<String, Object> proxies = mapChild(root, "proxies");
        Map<String, Object> velocity = mapChild(proxies, "velocity");
        velocity.put("enabled", enabled);
        velocity.put("online-mode", velocityOnlineMode);
        velocity.put("secret", secret == null ? "" : secret);
        // Ensure Bungee forwarding stays off when Velocity modern is used
        Map<String, Object> bungee = mapChild(proxies, "bungee-cord");
        if (!bungee.containsKey("online-mode")) {
            bungee.put("online-mode", true);
        }
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(global), StandardCharsets.UTF_8)) {
            yaml.dump(root, w);
        }
    }

    @SuppressWarnings("unchecked")
    private static void ensureSpigotBungeeOff(Path paperDir) throws IOException {
        Path spigot = paperDir.resolve("spigot.yml");
        if (!Files.isRegularFile(spigot)) {
            // Seed minimal settings so first boot doesn't enable BungeeCord by mistake
            String seed = """
                    settings:
                      bungeecord: false
                    """;
            Files.writeString(spigot, seed, StandardCharsets.UTF_8);
            return;
        }
        Yaml yaml = paperYaml();
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(spigot)) {
            Object loaded = yaml.load(in);
            root = loaded instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
        }
        Map<String, Object> settings = mapChild(root, "settings");
        settings.put("bungeecord", false);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(spigot), StandardCharsets.UTF_8)) {
            yaml.dump(root, w);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapChild(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (child instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
            parent.put(key, copy);
            return copy;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private static Yaml paperYaml() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        return new Yaml(opts);
    }
}
