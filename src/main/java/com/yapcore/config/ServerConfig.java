package com.yapcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        props.setProperty("max-players", "100");
        props.setProperty("ram-mb", "2048");
        props.setProperty("ram-min-mb", "512");
        props.setProperty("view-distance", "10");
        props.setProperty("motd", "YaPcore 12-Thread Engine");
        props.setProperty("plugins-dir", "plugins");
        props.setProperty("modules-dir", "modules");
        props.setProperty("logs-dir", "logs");
        props.setProperty("online-mode", "false");
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
        // Resource / texture packs
        props.setProperty("resource-pack-enabled", "true");
        props.setProperty("resource-pack-dir", "resourcepacks");
        props.setProperty("resource-pack-file", "");
        props.setProperty("resource-pack-http-port", "8081");
        props.setProperty("resource-pack-public-host", "");
        props.setProperty("resource-pack-forced", "false");
        props.setProperty("resource-pack-prompt", "This server uses a resource pack for the best experience.");
        // Product path: Paper → YapEngine Phase 3 (tick on cores 3–6).
        props.setProperty("game-authority", "paper");
        props.setProperty("paper-embed", "true");
        props.setProperty("paper-same-jvm", "false");
        props.setProperty("paper-dir", "paper-kernel");
        props.setProperty("paper-port", "25567");
        props.setProperty("paper-version", "26.2");
        props.setProperty("paper-jar-url", "");
        props.setProperty("paper-ready-timeout-sec", "180");
        props.setProperty("paper-phase3-tick-bridge", "true");
        props.setProperty("paper-phase3-nms-tick", "true");
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
        return Math.max(1, parseInt("max-players", 100));
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
        return props.getProperty("resource-pack-file", "");
    }

    public void setResourcePackFile(String fileName) {
        props.setProperty("resource-pack-file", fileName == null ? "" : fileName);
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

    public boolean isResourcePackForced() {
        return Boolean.parseBoolean(props.getProperty("resource-pack-forced", "false"));
    }

    public void setResourcePackForced(boolean forced) {
        props.setProperty("resource-pack-forced", Boolean.toString(forced));
    }

    public String getResourcePackPrompt() {
        return props.getProperty("resource-pack-prompt",
                "This server uses a resource pack for the best experience.");
    }

    public void setResourcePackPrompt(String prompt) {
        props.setProperty("resource-pack-prompt", prompt == null ? "" : prompt);
    }

    /** Product path: Paper → YapEngine. See docs/PAPER_YAPENGINE_PORT.md */
    public GameAuthority getGameAuthority() {
        String raw = props.getProperty("game-authority");
        if (raw == null || raw.isBlank()) {
            if (Boolean.parseBoolean(props.getProperty("game-kernel-enabled", "false"))) {
                return GameAuthority.MOJANG;
            }
            return GameAuthority.PAPER;
        }
        return GameAuthority.parse(raw);
    }

    public void setGameAuthority(GameAuthority authority) {
        props.setProperty("game-authority", authority.name().toLowerCase());
    }

    public boolean isPaperAuthority() {
        return getGameAuthority() == GameAuthority.PAPER;
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

    /** Phase 3: register spatial tick bridge hooks while Paper still owns the game thread. */
    public boolean isPaperPhase3TickBridge() {
        return Boolean.parseBoolean(props.getProperty("paper-phase3-tick-bridge", "true"));
    }

    /**
     * Phase 3: interior entity tick under DLM leases on spatial cores (plugin/NMS driver).
     * Requires {@link #isPaperPhase3TickBridge()} and {@code lib/paper-*-yap.jar};
     * boot fails closed if the jar is missing (no silent accounting-only).
     */
    public boolean isPaperPhase3NmsTick() {
        return Boolean.parseBoolean(props.getProperty("paper-phase3-nms-tick", "true"));
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
            case PAPER -> getPaperPort();
            case MOJANG -> getGameKernelPort();
            case NATIVE -> getPort();
        };
    }

    /** True when JE should TCP-proxy to a loopback game process. */
    public boolean isWrappedGameProxy() {
        return switch (getGameAuthority()) {
            case MOJANG -> true;
            case PAPER -> !isPaperEmbed();
            case NATIVE -> false;
        };
    }

    /** YaPcore binds JE TCP for native or when proxying to a wrap. */
    public boolean isYaPcoreJavaListener() {
        return switch (getGameAuthority()) {
            case NATIVE -> true;
            case MOJANG -> true;
            case PAPER -> !isPaperEmbed();
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
