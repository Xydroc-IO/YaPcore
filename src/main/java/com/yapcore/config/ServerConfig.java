package com.yapcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Persistent server settings: RAM, player caps, network, paths.
 */
public final class ServerConfig {

    public static final Path DEFAULT_DIR = Path.of("config");
    public static final Path DEFAULT_FILE = DEFAULT_DIR.resolve("server.properties");

    private final Path file;
    private final Properties props = new Properties();
    private final CopyOnWriteArrayList<Consumer<ServerConfig>> listeners = new CopyOnWriteArrayList<>();

    public ServerConfig(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public static ServerConfig loadOrCreate(Path file) throws IOException {
        ServerConfig cfg = new ServerConfig(file);
        if (Files.exists(file)) {
            cfg.load();
        } else {
            cfg.applyDefaults();
            cfg.save();
        }
        return cfg;
    }

    public void addListener(Consumer<ServerConfig> listener) {
        listeners.add(listener);
    }

    public Path getFile() {
        return file;
    }

    public void load() throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            props.clear();
            props.load(in);
        }
        applyMissingDefaults();
        fireChanged();
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaPcore server configuration");
        }
        fireChanged();
    }

    private void fireChanged() {
        for (Consumer<ServerConfig> listener : listeners) {
            listener.accept(this);
        }
    }

    private void applyDefaults() {
        props.setProperty("server-name", "YaPcore");
        props.setProperty("bind-host", "0.0.0.0");
        props.setProperty("port", "25566");
        props.setProperty("max-players", "300");
        props.setProperty("ram-mb", "2048");
        props.setProperty("ram-min-mb", "512");
        props.setProperty("view-distance", "10");
        props.setProperty("motd", "YaPcore 12-Thread Engine");
        props.setProperty("plugins-dir", "plugins");
        props.setProperty("modules-dir", "modules");
        props.setProperty("logs-dir", "logs");
        props.setProperty("online-mode", "false");
        // Do not OP everyone by default — use ops= or console `op <name>`
        props.setProperty("auto-op", "false");
        props.setProperty("ops", "");
        props.setProperty("gui-enabled", "true");
        // Dual-stack clients (on by default)
        props.setProperty("java-enabled", "true");
        props.setProperty("bedrock-enabled", "true");
        props.setProperty("bedrock-port", "25566");
        props.setProperty("shared-listen-port", "true");
        props.setProperty("crossplay-enabled", "true");
        props.setProperty("allow-localhost", "true");
        props.setProperty("nginx-public-port", "25565");
        props.setProperty("nginx-pack-port", "80");
        props.setProperty("nginx-domain", "");
        props.setProperty("backwards-compatible", "true");
        // Paper 1.20–1.21 plugin jars → 26.2 (field + CraftBukkit package rewrite)
        props.setProperty("plugin-compat-enabled", "true");
        props.setProperty("plugin-compat-rewrite", "true");
        props.setProperty("plugin-compat-backup", "true");
        // Resource / texture packs
        props.setProperty("resource-pack-enabled", "true");
        props.setProperty("resource-pack-dir", "resourcepacks");
        props.setProperty("resource-pack-file", "yapcore-default.zip");
        // Comma-separated ordered actives (overrides single file when non-empty). Later packs win on conflicts.
        props.setProperty("resource-pack-files", "yapcore-default.zip");
        props.setProperty("resource-pack-http-port", "8081");
        props.setProperty("resource-pack-public-host", "");
        // Absolute URL override (optional). {file} → active pack file name.
        // Prefer this when Cloudflare HTTPS (443) is broken — e.g. http://host:8081/pack/{file}
        props.setProperty("resource-pack-url", "");
        props.setProperty("resource-pack-forced", "false");
        props.setProperty("resource-pack-prompt",
                "This server offers a resource pack. Click Yes to download, or No to play without it.");
        // Product path: Folia owns game tick; YapEngine is chassis only.
        props.setProperty("game-authority", "folia");
        props.setProperty("folia-embed", "true");
        props.setProperty("folia-dir", "folia-kernel");
        props.setProperty("folia-port", "25567");
        props.setProperty("folia-version", "26.2");
        props.setProperty("folia-jar-url", "");
        props.setProperty("folia-ready-timeout-sec", "180");
        // Legacy Paper path (game-authority=paper)
        props.setProperty("paper-embed", "true");
        props.setProperty("paper-same-jvm", "false");
        props.setProperty("paper-dir", "paper-kernel");
        props.setProperty("paper-port", "25567");
        props.setProperty("paper-version", "26.2");
        props.setProperty("paper-jar-url", "");
        props.setProperty("paper-ready-timeout-sec", "180");
        props.setProperty("paper-phase3-tick-bridge", "false");
        props.setProperty("paper-phase3-nms-tick", "false");
        // Phase 4: first-party Via\* + Geyser parity (no plugin jars)
        props.setProperty("protocol-via-enabled", "true");
        props.setProperty("protocol-geyser-enabled", "true");
        // Legacy Mojang kernel (only when game-authority=mojang)
        props.setProperty("game-kernel-enabled", "false");
        props.setProperty("game-kernel-dir", "game-kernel");
        props.setProperty("game-kernel-port", "25567");
        props.setProperty("game-kernel-version", "26.2");
        props.setProperty("game-kernel-jar-url", "");
        props.setProperty("game-kernel-ready-timeout-sec", "180");
        // Internet / domain advertisement (bind vs public NAT)
        props.setProperty("internet-exposed", "false");
        props.setProperty("public-host", "");
        props.setProperty("server-domain", "");
        props.setProperty("public-port", "0");
        props.setProperty("public-bedrock-port", "0");
        props.setProperty("public-pack-port", "0");
        props.setProperty("srv-enabled", "true");
        props.setProperty("srv-priority", "0");
        props.setProperty("srv-weight", "5");
        // Velocity modern forwarding (Paper backend) — see docs/VELOCITY.md
        props.setProperty("velocity-enabled", "false");
        props.setProperty("velocity-secret", "");
        props.setProperty("velocity-secret-file", "");
        props.setProperty("velocity-online-mode", "true");
        props.setProperty("velocity-bind-localhost", "true");
        // Headless web control dashboard (mirrors Swing ControlPanel)
        props.setProperty("web-dashboard-enabled", "true");
        props.setProperty("web-dashboard-port", "8080");
        props.setProperty("web-dashboard-bind", "0.0.0.0");
        props.setProperty("web-dashboard-token", "");
        props.setProperty("web-dashboard-localhost-only", "false");
        // LuckPerms YaP group pack — apply once after Paper is up when LP jar is present
        props.setProperty("yap-ranks-auto-apply", "false");
    }

    private void applyMissingDefaults() {
        Properties defaults = new Properties();
        ServerConfig tmp = new ServerConfig(file);
        tmp.applyDefaults();
        defaults.putAll(tmp.props);
        for (String key : defaults.stringPropertyNames()) {
            if (!props.containsKey(key)) {
                props.setProperty(key, defaults.getProperty(key));
            }
        }
    }

    public String getServerName() {
        return props.getProperty("server-name", "YaPcore");
    }

    public void setServerName(String value) {
        props.setProperty("server-name", value);
    }

    public String getBindHost() {
        return props.getProperty("bind-host", "0.0.0.0");
    }

    public void setBindHost(String host) {
        props.setProperty("bind-host", host == null || host.isBlank() ? "0.0.0.0" : host.trim());
    }

    /** When true, listen for remote clients and print public join URLs. */
    public boolean isInternetExposed() {
        return Boolean.parseBoolean(props.getProperty("internet-exposed", "false"));
    }

    public void setInternetExposed(boolean exposed) {
        props.setProperty("internet-exposed", Boolean.toString(exposed));
    }

    /** Public DNS name or IP players connect to (may differ from bind-host). */
    public String getPublicHost() {
        return props.getProperty("public-host", "");
    }

    public void setPublicHost(String host) {
        props.setProperty("public-host", host == null ? "" : host.trim());
    }

    /** Alias for public-host when you want an explicit domain field. */
    public String getServerDomain() {
        return props.getProperty("server-domain", "");
    }

    public void setServerDomain(String domain) {
        props.setProperty("server-domain", domain == null ? "" : domain.trim());
    }

    /** Advertised Java TCP port after NAT (0 = same as {@link #getPort()}). */
    public int getPublicPort() {
        return parseInt("public-port", 0);
    }

    public void setPublicPort(int port) {
        props.setProperty("public-port", Integer.toString(Math.max(0, port)));
    }

    public int getPublicBedrockPort() {
        return parseInt("public-bedrock-port", 0);
    }

    public void setPublicBedrockPort(int port) {
        props.setProperty("public-bedrock-port", Integer.toString(Math.max(0, port)));
    }

    public int getPublicPackPort() {
        return parseInt("public-pack-port", 0);
    }

    public void setPublicPackPort(int port) {
        props.setProperty("public-pack-port", Integer.toString(Math.max(0, port)));
    }

    public boolean isSrvEnabled() {
        return Boolean.parseBoolean(props.getProperty("srv-enabled", "true"));
    }

    public void setSrvEnabled(boolean enabled) {
        props.setProperty("srv-enabled", Boolean.toString(enabled));
    }

    public int getSrvPriority() {
        return parseInt("srv-priority", 0);
    }

    public int getSrvWeight() {
        return parseInt("srv-weight", 5);
    }

    public int getPort() {
        return parseInt("port", 25566);
    }

    public void setPort(int port) {
        props.setProperty("port", Integer.toString(port));
    }

    public int getMaxPlayers() {
        return Math.max(1, parseInt("max-players", 300));
    }

    public void setMaxPlayers(int max) {
        props.setProperty("max-players", Integer.toString(Math.max(1, max)));
    }

    /** JVM heap ceiling in megabytes (-Xmx). */
    public int getRamMb() {
        return Math.max(256, parseInt("ram-mb", 2048));
    }

    public void setRamMb(int mb) {
        props.setProperty("ram-mb", Integer.toString(Math.max(256, mb)));
    }

    /** JVM heap floor in megabytes (-Xms). */
    public int getRamMinMb() {
        return Math.max(128, Math.min(getRamMb(), parseInt("ram-min-mb", 512)));
    }

    public void setRamMinMb(int mb) {
        props.setProperty("ram-min-mb", Integer.toString(Math.max(128, mb)));
    }

    public int getViewDistance() {
        return parseInt("view-distance", 10);
    }

    public void setViewDistance(int chunks) {
        props.setProperty("view-distance", Integer.toString(Math.max(2, Math.min(32, chunks))));
    }

    public String getMotd() {
        return props.getProperty("motd", "YaPcore");
    }

    public void setMotd(String motd) {
        props.setProperty("motd", motd);
    }

    public Path getPluginsDir() {
        return Path.of(props.getProperty("plugins-dir", "plugins"));
    }

    public Path getModulesDir() {
        return Path.of(props.getProperty("modules-dir", "modules"));
    }

    public Path getLogsDir() {
        return Path.of(props.getProperty("logs-dir", "logs"));
    }

    public boolean isOnlineMode() {
        return Boolean.parseBoolean(props.getProperty("online-mode", "false"));
    }

    public void setOnlineMode(boolean online) {
        props.setProperty("online-mode", Boolean.toString(online));
    }

    /**
     * When true, joining players are granted OP so vanilla commands like
     * {@code /gamemode} appear. Default {@code false} — use {@code ops=} or
     * console {@code op <name>} instead.
     */
    public boolean isAutoOp() {
        return Boolean.parseBoolean(props.getProperty("auto-op", "false"));
    }

    /** Comma-separated names always written into {@code ops.json} at Paper start. */
    public java.util.List<String> getOps() {
        String raw = props.getProperty("ops", "");
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String p : raw.split(",")) {
            String n = p.trim();
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * When true, YaPcore configures the Paper game for Velocity modern player-info
     * forwarding ({@code paper-global.yml} + {@code online-mode=false}).
     */
    public boolean isVelocityEnabled() {
        return Boolean.parseBoolean(props.getProperty("velocity-enabled", "false"));
    }

    public void setVelocityEnabled(boolean enabled) {
        props.setProperty("velocity-enabled", Boolean.toString(enabled));
    }

    /** Inline forwarding secret (same value as Velocity {@code forwarding.secret}). */
    public String getVelocitySecret() {
        return props.getProperty("velocity-secret", "");
    }

    public void setVelocitySecret(String secret) {
        props.setProperty("velocity-secret", secret == null ? "" : secret.trim());
    }

    /**
     * Optional path to a secret file (repo-relative or absolute). Preferred over
     * {@link #getVelocitySecret()} when non-blank.
     */
    public String getVelocitySecretFile() {
        return props.getProperty("velocity-secret-file", "");
    }

    public void setVelocitySecretFile(String path) {
        props.setProperty("velocity-secret-file", path == null ? "" : path.trim());
    }

    /**
     * Must match Velocity {@code online-mode}. True = trust Mojang-auth'd identities
     * from the proxy (usual production setting).
     */
    public boolean isVelocityOnlineMode() {
        return Boolean.parseBoolean(props.getProperty("velocity-online-mode", "true"));
    }

    public void setVelocityOnlineMode(boolean online) {
        props.setProperty("velocity-online-mode", Boolean.toString(online));
    }

    /**
     * When Velocity is enabled, bind Paper JE to {@code 127.0.0.1} so only the proxy
     * (on the same host) can reach the backend.
     */
    public boolean isVelocityBindLocalhost() {
        return Boolean.parseBoolean(props.getProperty("velocity-bind-localhost", "true"));
    }

    public void setVelocityBindLocalhost(boolean localhostOnly) {
        props.setProperty("velocity-bind-localhost", Boolean.toString(localhostOnly));
    }

    public boolean isJavaEnabled() {
        return Boolean.parseBoolean(props.getProperty("java-enabled", "true"));
    }

    public void setJavaEnabled(boolean enabled) {
        props.setProperty("java-enabled", Boolean.toString(enabled));
    }

    public boolean isBedrockEnabled() {
        return Boolean.parseBoolean(props.getProperty("bedrock-enabled", "true"));
    }

    public void setBedrockEnabled(boolean enabled) {
        props.setProperty("bedrock-enabled", Boolean.toString(enabled));
    }

    public int getBedrockPort() {
        return parseInt("bedrock-port", 25566);
    }

    public void setBedrockPort(int port) {
        props.setProperty("bedrock-port", Integer.toString(port));
    }

    /**
     * When true, Bedrock UDP binds the same port number as Java TCP
     * (streamlined one-address crossplay — OS allows TCP+UDP on one port).
     */
    public boolean isSharedListenPort() {
        return Boolean.parseBoolean(props.getProperty("shared-listen-port", "true"));
    }

    public void setSharedListenPort(boolean shared) {
        props.setProperty("shared-listen-port", Boolean.toString(shared));
    }

    /** Effective Bedrock UDP listen/advertise port. */
    public int effectiveBedrockPort() {
        return isSharedListenPort() ? getPort() : getBedrockPort();
    }

    /** Geyser-class shared-world crossplay. */
    public boolean isCrossplayEnabled() {
        return Boolean.parseBoolean(props.getProperty("crossplay-enabled", "true"));
    }

    public void setCrossplayEnabled(boolean enabled) {
        props.setProperty("crossplay-enabled", Boolean.toString(enabled));
    }

    /** Prefer loopback-friendly bind so same-PC clients can always join. */
    public boolean isAllowLocalhost() {
        return Boolean.parseBoolean(props.getProperty("allow-localhost", "true"));
    }

    public void setAllowLocalhost(boolean allow) {
        props.setProperty("allow-localhost", Boolean.toString(allow));
    }

    public int getNginxPublicPort() {
        return parseInt("nginx-public-port", 25565);
    }

    public void setNginxPublicPort(int port) {
        props.setProperty("nginx-public-port", Integer.toString(Math.max(1, port)));
    }

    public int getNginxPackPort() {
        return parseInt("nginx-pack-port", 80);
    }

    public void setNginxPackPort(int port) {
        props.setProperty("nginx-pack-port", Integer.toString(Math.max(1, port)));
    }

    public String getNginxDomain() {
        return props.getProperty("nginx-domain", "");
    }

    public void setNginxDomain(String domain) {
        props.setProperty("nginx-domain", domain == null ? "" : domain.trim());
    }

    public boolean isBackwardsCompatible() {
        return Boolean.parseBoolean(props.getProperty("backwards-compatible", "true"));
    }

    public void setBackwardsCompatible(boolean enabled) {
        props.setProperty("backwards-compatible", Boolean.toString(enabled));
    }

    /** Tier A/B: rewrite 1.20–1.21 plugin jars for Paper 26.2 before load. */
    public boolean isPluginCompatEnabled() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-enabled", "true"));
    }

    public void setPluginCompatEnabled(boolean enabled) {
        props.setProperty("plugin-compat-enabled", Boolean.toString(enabled));
    }

    public boolean isPluginCompatRewrite() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-rewrite", "true"));
    }

    public void setPluginCompatRewrite(boolean enabled) {
        props.setProperty("plugin-compat-rewrite", Boolean.toString(enabled));
    }

    public boolean isPluginCompatBackup() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-backup", "true"));
    }

    public void setPluginCompatBackup(boolean enabled) {
        props.setProperty("plugin-compat-backup", Boolean.toString(enabled));
    }

    public boolean isResourcePackEnabled() {
        return Boolean.parseBoolean(props.getProperty("resource-pack-enabled", "true"));
    }

    public void setResourcePackEnabled(boolean enabled) {
        props.setProperty("resource-pack-enabled", Boolean.toString(enabled));
    }

    public Path getResourcePackDir() {
        return Path.of(props.getProperty("resource-pack-dir", "resourcepacks"));
    }

    public String getResourcePackFile() {
        List<String> files = getResourcePackFiles();
        return files.isEmpty() ? props.getProperty("resource-pack-file", "") : files.get(0);
    }

    public void setResourcePackFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            setResourcePackFiles(List.of());
            return;
        }
        setResourcePackFiles(List.of(fileName.trim()));
    }

    /** Ordered active pack zip names. Empty = no packs. */
    public List<String> getResourcePackFiles() {
        String multi = props.getProperty("resource-pack-files", "");
        List<String> out = new ArrayList<>();
        if (multi != null && !multi.isBlank()) {
            for (String part : multi.split(",")) {
                String n = part.trim();
                if (!n.isEmpty() && !out.contains(n)) {
                    out.add(n);
                }
            }
        }
        if (out.isEmpty()) {
            String single = props.getProperty("resource-pack-file", "");
            if (single != null && !single.isBlank()) {
                out.add(single.trim());
            }
        }
        return List.copyOf(out);
    }

    public void setResourcePackFiles(List<String> fileNames) {
        List<String> clean = new ArrayList<>();
        if (fileNames != null) {
            for (String n : fileNames) {
                if (n == null) {
                    continue;
                }
                String t = n.trim();
                if (!t.isEmpty() && !clean.contains(t)) {
                    clean.add(t);
                }
            }
        }
        props.setProperty("resource-pack-files", String.join(",", clean));
        props.setProperty("resource-pack-file", clean.isEmpty() ? "" : clean.get(0));
    }

    public int getResourcePackHttpPort() {
        return parseInt("resource-pack-http-port", 8081);
    }

    public void setResourcePackHttpPort(int port) {
        props.setProperty("resource-pack-http-port", Integer.toString(port));
    }

    public String getResourcePackPublicHost() {
        return props.getProperty("resource-pack-public-host", "");
    }

    public void setResourcePackPublicHost(String host) {
        props.setProperty("resource-pack-public-host", host == null ? "" : host);
    }

    /**
     * Optional absolute pack URL. Use {@code {file}} for the active zip name.
     * Empty → build from public host / pack port (see {@code PublicEndpoint#packUrl}).
     */
    public String getResourcePackUrl() {
        return props.getProperty("resource-pack-url", "");
    }

    public void setResourcePackUrl(String url) {
        props.setProperty("resource-pack-url", url == null ? "" : url);
    }

    public boolean isResourcePackForced() {
        return Boolean.parseBoolean(props.getProperty("resource-pack-forced", "false"));
    }

    public void setResourcePackForced(boolean forced) {
        props.setProperty("resource-pack-forced", Boolean.toString(forced));
    }

    public boolean isWebDashboardEnabled() {
        // MSPT benches: no dashboard bind (8080) and avoid JLine stdin when headless.
        String bench = System.getProperty("yap.bench.scenario");
        if (bench != null && !bench.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(props.getProperty("web-dashboard-enabled", "true"));
    }

    public void setWebDashboardEnabled(boolean enabled) {
        props.setProperty("web-dashboard-enabled", Boolean.toString(enabled));
    }

    public int getWebDashboardPort() {
        return parseInt("web-dashboard-port", 8080);
    }

    public void setWebDashboardPort(int port) {
        props.setProperty("web-dashboard-port", Integer.toString(port));
    }

    public String getWebDashboardBind() {
        return props.getProperty("web-dashboard-bind", "0.0.0.0");
    }

    public void setWebDashboardBind(String bind) {
        props.setProperty("web-dashboard-bind", bind == null ? "0.0.0.0" : bind);
    }

    public String getWebDashboardToken() {
        return props.getProperty("web-dashboard-token", "");
    }

    public void setWebDashboardToken(String token) {
        props.setProperty("web-dashboard-token", token == null ? "" : token);
    }

    public boolean isWebDashboardLocalhostOnly() {
        return Boolean.parseBoolean(props.getProperty("web-dashboard-localhost-only", "false"));
    }

    public void setWebDashboardLocalhostOnly(boolean localhostOnly) {
        props.setProperty("web-dashboard-localhost-only", Boolean.toString(localhostOnly));
    }

    /** When true, apply examples/luckperms pack once after Paper start if LuckPerms is installed. */
    public boolean isYapRanksAutoApply() {
        return Boolean.parseBoolean(props.getProperty("yap-ranks-auto-apply", "false"));
    }

    public void setYapRanksAutoApply(boolean enabled) {
        props.setProperty("yap-ranks-auto-apply", Boolean.toString(enabled));
    }

    public String getResourcePackPrompt() {
        return props.getProperty("resource-pack-prompt",
                "This server uses a resource pack for the best experience.");
    }

    public void setResourcePackPrompt(String prompt) {
        props.setProperty("resource-pack-prompt", prompt == null ? "" : prompt);
    }

    /** Product path: Folia game + YapEngine chassis. See Folia plan. */
    public GameAuthority getGameAuthority() {
        String raw = props.getProperty("game-authority");
        if (raw == null || raw.isBlank()) {
            if (Boolean.parseBoolean(props.getProperty("game-kernel-enabled", "false"))) {
                return GameAuthority.MOJANG;
            }
            return GameAuthority.FOLIA;
        }
        return GameAuthority.parse(raw);
    }

    public void setGameAuthority(GameAuthority authority) {
        props.setProperty("game-authority", authority.name().toLowerCase());
    }

    public boolean isFoliaAuthority() {
        return getGameAuthority() == GameAuthority.FOLIA;
    }

    public boolean isPaperAuthority() {
        return getGameAuthority() == GameAuthority.PAPER;
    }

    public boolean isFoliaEmbed() {
        return Boolean.parseBoolean(props.getProperty("folia-embed", "true"));
    }

    public void setFoliaEmbed(boolean embed) {
        props.setProperty("folia-embed", Boolean.toString(embed));
    }

    public String getFoliaDir() {
        String override = System.getProperty("yapcore.folia.dir");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return props.getProperty("folia-dir", "folia-kernel");
    }

    public int getFoliaPort() {
        return parseInt("folia-port", 25567);
    }

    public String getFoliaVersion() {
        return props.getProperty("folia-version", "26.2");
    }

    public String getFoliaJarUrl() {
        return props.getProperty("folia-jar-url", "");
    }

    public int getFoliaReadyTimeoutSec() {
        return parseInt("folia-ready-timeout-sec", 180);
    }

    /**
     * Port Folia binds. Via front + Folia authority → loopback {@link #getFoliaPort()};
     * otherwise public {@link #getPort()} when embed.
     */
    public int foliaListenPort() {
        if (isFoliaAuthority() && isProtocolViaEnabled()) {
            return getFoliaPort();
        }
        return isFoliaEmbed() ? getPort() : getFoliaPort();
    }

    /** Phase 2 Paper: owns public JE port when authority=paper. */
    public boolean isPaperEmbed() {
        return Boolean.parseBoolean(props.getProperty("paper-embed", "true"));
    }

    public void setPaperEmbed(boolean embed) {
        props.setProperty("paper-embed", Boolean.toString(embed));
    }

    public boolean isPaperSameJvm() {
        return Boolean.parseBoolean(props.getProperty("paper-same-jvm", "false"));
    }

    /** Phase 3 spatial tick — retired as product path (defaults off). Legacy benches may re-enable. */
    public boolean isPaperPhase3TickBridge() {
        return Boolean.parseBoolean(props.getProperty("paper-phase3-tick-bridge", "false"));
    }

    /**
     * Phase 3 NMS spatial tick — retired as product path (defaults off).
     * Requires {@link #isPaperPhase3TickBridge()} and {@code lib/paper-*-yap.jar}.
     */
    public boolean isPaperPhase3NmsTick() {
        return Boolean.parseBoolean(props.getProperty("paper-phase3-nms-tick", "false"));
    }

    /**
     * Phase 4 Via\* parity front door. When true under Folia/Paper authority, YaPcore owns
     * the public JE port and proxies (with remap) to the game on folia-port / paper-port.
     * <p>
     * Disabled under most MSPT benches so stock Paper and YaP hit the same socket path.
     * <strong>Exception: {@code highpop}</strong> — keep native Via front so forks pay
     * Via\* plugin cost while YaP uses ProtocolCompat (fair product-surface compare).
     */
    public boolean isProtocolViaEnabled() {
        String bench = System.getProperty("yap.bench.scenario");
        if (bench != null && !bench.isBlank()) {
            if ("highpop".equalsIgnoreCase(bench.trim())) {
                return Boolean.parseBoolean(props.getProperty("protocol-via-enabled", "true"));
            }
            return false;
        }
        return Boolean.parseBoolean(props.getProperty("protocol-via-enabled", "true"));
    }

    public void setProtocolViaEnabled(boolean enabled) {
        props.setProperty("protocol-via-enabled", Boolean.toString(enabled));
    }

    /** Phase 4 Geyser parity — expand RakNet/BE codecs when true. */
    public boolean isProtocolGeyserEnabled() {
        return Boolean.parseBoolean(props.getProperty("protocol-geyser-enabled", "true"));
    }

    public void setProtocolGeyserEnabled(boolean enabled) {
        props.setProperty("protocol-geyser-enabled", Boolean.toString(enabled));
    }

    /**
     * Port Paper actually binds. With Via front + Paper authority → loopback
     * {@link #getPaperPort()}; otherwise public {@link #getPort()} when embed.
     */
    public int paperListenPort() {
        if (isPaperAuthority() && isProtocolViaEnabled()) {
            return getPaperPort();
        }
        return isPaperEmbed() ? getPort() : getPaperPort();
    }

    public boolean isNativeAuthority() {
        return getGameAuthority() == GameAuthority.NATIVE;
    }

    public String getPaperDir() {
        String override = System.getProperty("yapcore.paper.dir");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return props.getProperty("paper-dir", "paper-kernel");
    }

    public int getPaperPort() {
        return parseInt("paper-port", 25567);
    }

    public String getPaperVersion() {
        return props.getProperty("paper-version", "26.2");
    }

    public String getPaperJarUrl() {
        return props.getProperty("paper-jar-url", "");
    }

    public int getPaperReadyTimeoutSec() {
        return parseInt("paper-ready-timeout-sec", 180);
    }

    public int getWrappedGamePort() {
        return switch (getGameAuthority()) {
            case FOLIA -> getFoliaPort();
            case PAPER -> getPaperPort();
            case MOJANG -> getGameKernelPort();
            case NATIVE -> getPort();
        };
    }

    /** True when JE should TCP-proxy to a loopback game process. */
    public boolean isWrappedGameProxy() {
        return switch (getGameAuthority()) {
            case MOJANG -> true;
            case FOLIA -> !isFoliaEmbed() || isProtocolViaEnabled();
            case PAPER -> !isPaperEmbed() || isProtocolViaEnabled();
            case NATIVE -> false;
        };
    }

    /** YaPcore binds JE TCP for native, wrap proxy, or Via front of Folia/Paper. */
    public boolean isYaPcoreJavaListener() {
        return switch (getGameAuthority()) {
            case NATIVE -> true;
            case MOJANG -> true;
            case FOLIA -> !isFoliaEmbed() || isProtocolViaEnabled();
            case PAPER -> !isPaperEmbed() || isProtocolViaEnabled();
        };
    }

    public boolean isGameKernelEnabled() {
        return getGameAuthority() == GameAuthority.MOJANG;
    }

    public void setGameKernelEnabled(boolean enabled) {
        props.setProperty("game-kernel-enabled", Boolean.toString(enabled));
    }

    public String getGameKernelDir() {
        return props.getProperty("game-kernel-dir", "game-kernel");
    }

    public int getGameKernelPort() {
        return parseInt("game-kernel-port", 25567);
    }

    public String getGameKernelVersion() {
        return props.getProperty("game-kernel-version", "26.2");
    }

    public String getGameKernelJarUrl() {
        return props.getProperty("game-kernel-jar-url", "");
    }

    public int getGameKernelReadyTimeoutSec() {
        return parseInt("game-kernel-ready-timeout-sec", 180);
    }

    public String get(String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    private int parseInt(String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
