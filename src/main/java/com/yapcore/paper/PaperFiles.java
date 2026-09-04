package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import com.yapcore.fill.FillClient;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.resourcepack.ResourcePackBundler;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/** Shared Paper jar / eula / server.properties helpers for wrap + embed. */
public final class PaperFiles {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperFiles");

    private PaperFiles() {
    }

    public static Path ensurePaperJar(Path rootDir, Path paperDir, ServerConfig config) throws IOException {
        String version = config.getPaperVersion();
        Files.createDirectories(paperDir);

        // Prefer cached stock Paper jar under lib/
        Path yapCached = rootDir.resolve("lib").resolve("paper-" + version + "-yap.jar");
        Path jar = paperDir.resolve("paper-" + version + ".jar");
        if (Files.isRegularFile(yapCached) && Files.size(yapCached) > 1_000_000) {
            Files.copy(yapCached, jar, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Using cached Paper jar → " + yapCached.getFileName());
            return jar;
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

    /**
     * Write Paper {@code server.properties}. When {@code rootDir} is set, syncs YaPcore
     * resource-pack settings so JE clients auto-download on join (Paper owns the protocol).
     */
    public static void writeServerProperties(Path rootDir, Path dir, ServerConfig config,
                                             int listenPort, String bindIp, String comment)
            throws IOException {
        Path file = dir.resolve("server.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        String effectiveBind = effectivePaperBind(config, bindIp);
        boolean bench = System.getProperty("yap.bench.scenario") != null
                && !System.getProperty("yap.bench.scenario").isBlank();
        if (bench) {
            // MSPT scoreboard owns port / view / seed — do not clobber with product config.
            if (!p.containsKey("server-port")) {
                p.setProperty("server-port", Integer.toString(listenPort));
            }
            if (!p.containsKey("server-ip")) {
                p.setProperty("server-ip", effectiveBind == null ? "" : effectiveBind);
            }
        } else {
            p.setProperty("server-port", Integer.toString(listenPort));
            p.setProperty("server-ip", effectiveBind == null ? "" : effectiveBind);
        }
        // Velocity authenticates; Paper must be offline-mode when modern forwarding is on.
        boolean online = config.isVelocityEnabled() ? false : config.isOnlineMode();
        p.setProperty("online-mode", Boolean.toString(online));
        // Offline mode / Via / dual-stack cannot present Mojang chat-signing keys.
        // enforce-secure-profile=true → join failures or "Chat messages cannot be verified".
        boolean secureProfile = online && !config.isProtocolViaEnabled();
        p.setProperty("enforce-secure-profile", Boolean.toString(secureProfile));
        if (!secureProfile) {
            LOG.info("Paper enforce-secure-profile=false (online-mode=" + online
                    + ", via=" + config.isProtocolViaEnabled() + ")");
        }
        if (config.isVelocityEnabled()) {
            p.setProperty("prevent-proxy-connections", "false");
        }
        if (!bench) {
            p.setProperty("max-players", Integer.toString(config.getMaxPlayers()));
            p.setProperty("view-distance", Integer.toString(config.getViewDistance()));
            p.setProperty("simulation-distance", Integer.toString(config.getViewDistance()));
            p.setProperty("motd", config.getMotd());
        } else {
            if (!p.containsKey("max-players")) {
                p.setProperty("max-players", "20");
            }
            if (!p.containsKey("view-distance")) {
                p.setProperty("view-distance", "6");
            }
            if (!p.containsKey("simulation-distance")) {
                p.setProperty("simulation-distance", p.getProperty("view-distance", "6"));
            }
        }
        if (!bench) {
            // Product: open spawn for ops tooling. Bench: leave Paper default (16) so
            // dig/grief parity matches stock Paper competitors.
            p.setProperty("spawn-protection", "0");
        }
        p.setProperty("enable-command-block", "true");
        applyResourcePack(p, rootDir, config);
        try (var out = Files.newOutputStream(file)) {
            p.store(out, comment);
        }
    }

    /** @deprecated prefer {@link #writeServerProperties(Path, Path, ServerConfig, int, String, String)} */
    @Deprecated
    public static void writeServerProperties(Path dir, ServerConfig config, int listenPort, String bindIp, String comment)
            throws IOException {
        writeServerProperties(null, dir, config, listenPort, bindIp, comment);
    }

    /**
     * Push the effective offer pack into Paper {@code server.properties} (Yes/No login prompt).
     * Multiple actives are merged into one zip so clients get every pack without play-phase push.
     */
    static void applyResourcePack(Properties p, Path rootDir, ServerConfig config) {
        if (rootDir == null || !config.isResourcePackEnabled()) {
            clearPackProps(p);
            return;
        }
        List<String> files = config.getResourcePackFiles();
        if (files.isEmpty()) {
            clearPackProps(p);
            LOG.info("Paper resource packs: none active");
            return;
        }
        Path packsRoot = rootDir.resolve(config.getResourcePackDir()).toAbsolutePath().normalize();
        try {
            String fileName = ResourcePackBundler.ensureOfferFile(
                    rootDir.resolve(config.getResourcePackDir()), files);
            if (fileName == null || fileName.isBlank()) {
                clearPackProps(p);
                return;
            }
            Path pack = rootDir.resolve(config.getResourcePackDir()).resolve(fileName).normalize();
            if (!pack.toAbsolutePath().normalize().startsWith(packsRoot) || !Files.isRegularFile(pack)) {
                LOG.warning("Offer resource pack missing: " + pack);
                clearPackProps(p);
                return;
            }
            String url = new PublicEndpoint(config).packUrl(fileName);
            String sha1;
            if (looksAbsoluteHttp(url)) {
                sha1 = sha1HexFromUrl(url);
                LOG.info("Resource pack SHA-1 from remote URL (matches what clients download)");
            } else {
                sha1 = sha1Hex(pack);
            }
            String prompt = config.getResourcePackPrompt();
            if (prompt == null || prompt.isBlank()) {
                prompt = "This server offers a resource pack. Click Yes to download, or No to play without it.";
            }
            boolean forced = config.isResourcePackForced();
            UUID id = UUID.nameUUIDFromBytes(("yapcore-pack:" + fileName + ":" + sha1)
                    .getBytes(StandardCharsets.UTF_8));
            p.setProperty("resource-pack", url);
            p.setProperty("resource-pack-sha1", sha1);
            p.setProperty("resource-pack-id", id.toString());
            p.setProperty("resource-pack-prompt", jsonTextComponent(prompt));
            p.setProperty("require-resource-pack", Boolean.toString(forced));
            LOG.info("Paper resource pack (login prompt) → " + url + " sha1=" + sha1
                    + " required=" + forced
                    + (files.size() > 1 ? " [merged " + files.size() + " actives → " + fileName + "]" : ""));
        } catch (IOException e) {
            LOG.warning("Could not prepare resource pack offer: " + e.getMessage());
            clearPackProps(p);
        }
    }

    private static boolean looksAbsoluteHttp(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") || u.startsWith("http://");
    }

    /**
     * Hash the bytes clients will download (follows redirects — needed for GitHub
     * {@code /releases/latest/download/…} → CDN).
     */
    private static String sha1HexFromUrl(String url) throws IOException {
        URI uri = URI.create(url.trim());
        HttpURLConnection conn = null;
        try {
            conn = openFollowingRedirects(uri, 8);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = conn.getInputStream();
                 DigestInputStream din = new DigestInputStream(in, digest)) {
                din.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("SHA-1 failed for " + url + ": " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection openFollowingRedirects(URI start, int maxHops) throws IOException {
        URI uri = start;
        for (int hop = 0; hop < maxHops; hop++) {
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(180_000);
            conn.setRequestProperty("User-Agent", "YaPcore-ResourcePack/1.0");
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isBlank()) {
                    throw new IOException("Redirect without Location from " + uri);
                }
                uri = uri.resolve(loc);
                continue;
            }
            return conn;
        }
        throw new IOException("Too many redirects for " + start);
    }

    private static void clearPackProps(Properties p) {
        p.setProperty("resource-pack", "");
        p.setProperty("resource-pack-sha1", "");
        p.setProperty("resource-pack-id", "");
        p.setProperty("resource-pack-prompt", "");
        p.setProperty("require-resource-pack", "false");
    }

    private static String jsonTextComponent(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "{\"text\":\"" + escaped + "\"}";
    }

    private static String sha1Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream din = new DigestInputStream(in, digest)) {
                din.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("SHA-1 failed for " + path, e);
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
        // Via front: game stays on loopback; public JE is YaPcore's ViaProxyHandler.
        if ((config.isPaperAuthority() || config.isFoliaAuthority()) && config.isProtocolViaEnabled()) {
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
        String game = config.isFoliaAuthority() ? "Folia" : "Paper";
        LOG.info("Velocity/YaP Link modern forwarding enabled for " + game + " (online-mode="
                + config.isVelocityOnlineMode()
                + ", bind="
                + (config.isVelocityBindLocalhost() ? "127.0.0.1" : "config bind")
                + ") — players must join via YaP Link or Velocity");
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
