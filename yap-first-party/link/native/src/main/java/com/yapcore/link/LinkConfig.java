package com.yapcore.link;

import com.yapcore.link.config.LinkTomlLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** YaP Link configuration ({@code link.properties} or {@code link.toml} + forwarding secret). */
public final class LinkConfig {

    final Path home;
    final Properties props = new Properties();
    private String forwardingSecret = "";

    private LinkConfig(Path home) {
        this.home = home;
    }

    public static LinkConfig load(Path home) throws IOException {
        LinkConfig cfg = new LinkConfig(home.toAbsolutePath().normalize());
        Files.createDirectories(cfg.home);
        Path toml = cfg.home.resolve("link.toml");
        Path propsFile = cfg.home.resolve("link.properties");
        if (Files.isRegularFile(toml)) {
            cfg.props.putAll(LinkTomlLoader.load(toml));
        } else if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                cfg.props.load(in);
            }
        } else {
            LinkConfigDefaults.applyDefaults(cfg.props);
            cfg.saveProperties();
        }
        cfg.applyMissingDefaults();
        cfg.loadSecret();
        return cfg;
    }

    private void applyMissingDefaults() {
        Properties d = new Properties();
        LinkConfig tmp = new LinkConfig(home);
        LinkConfigDefaults.applyDefaults(tmp.props);
        d.putAll(tmp.props);
        for (String key : d.stringPropertyNames()) {
            if (!props.containsKey(key)) {
                props.setProperty(key, d.getProperty(key));
            }
        }
    }

    public void saveProperties() throws IOException {
        Path file = home.resolve("link.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaP Link — native network proxy");
        }
    }

    private void loadSecret() throws IOException {
        String mode = props.getProperty("player-info-forwarding-mode", "modern").trim().toLowerCase(Locale.ROOT);
        if (!"modern".equals(mode)) {
            throw new IOException("YaP Link supports player-info-forwarding-mode=modern only");
        }
        String fileName = props.getProperty("forwarding-secret-file", "forwarding.secret").trim();
        Path secretFile = home.resolve(fileName);
        if (!Files.isRegularFile(secretFile)) {
            String generated = randomSecret();
            Files.writeString(secretFile, generated + "\n", StandardCharsets.UTF_8);
            forwardingSecret = generated;
        } else {
            forwardingSecret = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
        }
        if (forwardingSecret.isBlank()) {
            throw new IOException("Empty forwarding secret in " + secretFile);
        }
    }

    private static String randomSecret() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public Path home() { return home; }

    public boolean pingPassthrough() { return bool("ping-passthrough", true); }

    public int backendProbeIntervalSec() { return intProp("backend-probe-interval-sec", 10); }

    public int backendProbeTimeoutMs() { return intProp("backend-probe-timeout-ms", 3000); }

    public int connectTimeoutMs() { return intProp("connect-timeout-ms", 10000); }

    public int loginTimeoutMs() { return intProp("login-timeout-ms", 30000); }

    public int readTimeoutSec() { return intProp("read-timeout-sec", 300); }

    public boolean skipDownOnForcedHost() { return bool("skip-down-on-forced-host", false); }

    public boolean aggregatePlayerCount() { return bool("aggregate-player-count", true); }

    public boolean globalTabList() { return bool("global-tab-list", false); }

    public boolean chatRelayEnabled() { return bool("chat-relay-enabled", true); }

    public String chatRelayChannel() { return props.getProperty("chat-relay-channel", "network"); }

    public String chatRelayFormat() { return props.getProperty("chat-relay-format", "[{server}] {name}: {message}"); }

    public boolean chatJoinAnnounce() { return bool("chat-join-announce", false); }

    public String forcedHostServer(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return null;
        }
        String key = "forced-host." + hostname.trim().toLowerCase(Locale.ROOT);
        String v = props.getProperty(key);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        // port-stripped host (lobby.example.com:25565 → lobby.example.com)
        int colon = hostname.indexOf(':');
        if (colon > 0) {
            return forcedHostServer(hostname.substring(0, colon));
        }
        return null;
    }

    public Map<String, String> forcedHosts() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("forced-host.")) {
                map.put(key.substring("forced-host.".length()), props.getProperty(key));
            }
        }
        return map;
    }

    public String bindHost() { return hostPart(props.getProperty("bind", "0.0.0.0:25565"), "0.0.0.0"); }

    public int bindPort() { return portPart(props.getProperty("bind", "0.0.0.0:25565"), 25565); }

    public String motd() { return props.getProperty("motd", "YaP Link"); }

    public int maxPlayers() { return intProp("max-players", 500); }

    public boolean onlineMode() { return bool("online-mode", false); }

    public boolean showPingRequests() { return bool("show-ping-requests", false); }

    public byte[] forwardingSecret() { return forwardingSecret.getBytes(StandardCharsets.UTF_8); }

    public String forwardingSecretString() { return forwardingSecret; }

    public Map<String, Backend> servers() {
        Map<String, Backend> map = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("servers.")) {
                continue;
            }
            String name = key.substring("servers.".length()).trim();
            if (!name.isEmpty() && !name.contains(".")) {
                map.put(name, Backend.parse(name, props.getProperty(key)));
            }
        }
        if (map.isEmpty()) {
            map.put("lobby", new Backend("lobby", "127.0.0.1", 25566));
        }
        return map;
    }

    public List<String> tryOrder() {
        String raw = props.getProperty("try", "lobby");
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        if (out.isEmpty()) {
            out.addAll(servers().keySet());
        }
        return out;
    }

    public Backend resolveTry() {
        Map<String, Backend> all = servers();
        for (String name : tryOrder()) {
            Backend b = all.get(name);
            if (b != null) {
                return b;
            }
        }
        return all.values().iterator().next();
    }

    public Backend findServer(String name) {
        if (name == null) {
            return null;
        }
        Map<String, Backend> all = servers();
        Backend exact = all.get(name);
        if (exact != null) {
            return exact;
        }
        for (var e : all.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public boolean enableServerCommand() { return bool("enable-server-command", true); }

    public String publicHost() {
        String h = props.getProperty("public-host", "127.0.0.1").trim();
        return h.isEmpty() ? "127.0.0.1" : h;
    }

    public int publicPort() {
        int p = intProp("public-port", 0);
        return p > 0 ? p : bindPort();
    }

    public String bedrockMode() { return LinkConfigBedrock.bedrockMode(this); }

    public boolean bedrockNativeEnabled() { return LinkConfigBedrock.bedrockNativeEnabled(this); }

    public boolean bedrockEnabled() { return LinkConfigBedrock.bedrockEnabled(this); }

    public boolean geyserEnabled() { return LinkConfigBedrock.geyserEnabled(this); }

    public String geyserHomeRel() { return LinkConfigBedrock.geyserHomeRel(this); }

    public String geyserJarName() { return LinkConfigBedrock.geyserJarName(this); }

    public String geyserRemoteHost() { return LinkConfigBedrock.geyserRemoteHost(this); }

    public int geyserRemotePort() { return LinkConfigBedrock.geyserRemotePort(this); }

    public String geyserBedrockBind() { return LinkConfigBedrock.geyserBedrockBind(this); }

    public String bedrockBindHost() { return LinkConfigBedrock.bedrockBindHost(this); }

    public int bedrockBindPort() { return LinkConfigBedrock.bedrockBindPort(this); }

    public java.util.List<java.net.InetSocketAddress> bedrockBindAddresses() { return LinkConfigBedrock.bedrockBindAddresses(this); }

    public String bedrockBackendHost() { return LinkConfigBedrock.bedrockBackendHost(this); }

    public int bedrockBackendPort() { return LinkConfigBedrock.bedrockBackendPort(this); }

    public int bedrockMotdProtocol() { return LinkConfigBedrock.bedrockMotdProtocol(this); }

    public String bedrockMotdVersion() { return LinkConfigBedrock.bedrockMotdVersion(this); }

    public String bedrockMotdSub() { return LinkConfigBedrock.bedrockMotdSub(this); }

    public boolean pluginsEnabled() { return LinkConfigBedrock.pluginsEnabled(this); }

    public Path floodgateKeyFile() { return LinkConfigBedrock.floodgateKeyFile(this); }

    public BedrockTarget bedrockBackendFor(String serverName) { return LinkConfigBedrock.bedrockBackendFor(this, serverName); }

    public boolean connectRateLimitEnabled() { return LinkConfigRateLimits.connectRateLimitEnabled(this); }

    public int connectRatePerIp() { return LinkConfigRateLimits.connectRatePerIp(this); }

    public long connectRateWindowMs() { return LinkConfigRateLimits.connectRateWindowMs(this); }

    public boolean handshakeRateLimitEnabled() { return LinkConfigRateLimits.handshakeRateLimitEnabled(this); }

    public int handshakeRatePerIp() { return LinkConfigRateLimits.handshakeRatePerIp(this); }

    public long handshakeRateWindowMs() { return LinkConfigRateLimits.handshakeRateWindowMs(this); }

    public boolean loginRateLimitEnabled() { return LinkConfigRateLimits.loginRateLimitEnabled(this); }

    public int loginRatePerIp() { return LinkConfigRateLimits.loginRatePerIp(this); }

    public long loginRateWindowMs() { return LinkConfigRateLimits.loginRateWindowMs(this); }

    public boolean rateLimitExemptLoopback() { return LinkConfigRateLimits.rateLimitExemptLoopback(this); }

    public boolean maxConcurrentPerIpEnabled() { return LinkConfigRateLimits.maxConcurrentPerIpEnabled(this); }

    public int maxConcurrentPerIp() { return LinkConfigRateLimits.maxConcurrentPerIp(this); }

    public boolean metricsHttpEnabled() { return LinkConfigRateLimits.metricsHttpEnabled(this); }

    public String metricsHttpBind() { return LinkConfigRateLimits.metricsHttpBind(this); }

    public int metricsHttpPort() { return LinkConfigRateLimits.metricsHttpPort(this); }

    public record BedrockTarget(String host, int port) {
        static BedrockTarget parse(String raw) {
            int colon = raw.lastIndexOf(':');
            if (colon <= 0) {
                return new BedrockTarget(raw.trim(), 19132);
            }
            return new BedrockTarget(
                    raw.substring(0, colon).trim(),
                    Integer.parseInt(raw.substring(colon + 1).trim()));
        }
    }

    boolean bool(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(def)));
    }

    int intProp(String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static String hostPart(String bind, String def) {
        if (bind == null || bind.isBlank()) {
            return def;
        }
        int colon = bind.lastIndexOf(':');
        return colon <= 0 ? bind.trim() : bind.substring(0, colon).trim();
    }

    static int portPart(String bind, int def) {
        if (bind == null || bind.isBlank()) {
            return def;
        }
        int colon = bind.lastIndexOf(':');
        if (colon < 0 || colon == bind.length() - 1) {
            return def;
        }
        try {
            return Integer.parseInt(bind.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public record Backend(String name, String host, int port) {
        static Backend parse(String name, String raw) {
            String v = raw == null ? "127.0.0.1:25566" : raw.trim();
            int colon = v.lastIndexOf(':');
            if (colon <= 0) {
                return new Backend(name, v, 25566);
            }
            return new Backend(name, v.substring(0, colon).trim(),
                    Integer.parseInt(v.substring(colon + 1).trim()));
        }
    }
}
