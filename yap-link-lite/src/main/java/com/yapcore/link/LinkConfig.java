package com.yapcore.link;

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
import java.util.Map;
import java.util.Properties;

/** YaP Link configuration ({@code link.properties} + {@code forwarding.secret}). */
public final class LinkConfig {

    private final Path home;
    private final Properties props = new Properties();
    private String forwardingSecret = "";

    private LinkConfig(Path home) {
        this.home = home;
    }

    public static LinkConfig load(Path home) throws IOException {
        LinkConfig cfg = new LinkConfig(home.toAbsolutePath().normalize());
        Files.createDirectories(cfg.home);
        Path file = cfg.home.resolve("link.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                cfg.props.load(in);
            }
        } else {
            cfg.applyDefaults();
            cfg.save();
        }
        cfg.applyMissingDefaults();
        cfg.loadSecret();
        return cfg;
    }

    private void applyDefaults() {
        props.setProperty("bind", "0.0.0.0:25565");
        props.setProperty("motd", "YaP Link");
        props.setProperty("max-players", "500");
        props.setProperty("online-mode", "false");
        props.setProperty("player-info-forwarding-mode", "modern");
        props.setProperty("forwarding-secret-file", "forwarding.secret");
        props.setProperty("show-ping-requests", "false");
        // servers.<name>=host:port
        props.setProperty("servers.lobby", "127.0.0.1:25566");
        props.setProperty("try", "lobby");
        props.setProperty("force-default-server", "true");
        props.setProperty("enable-server-command", "true");
        props.setProperty("public-host", "127.0.0.1");
        props.setProperty("public-port", "0"); // 0 = use bind port
        props.setProperty("bedrock-enabled", "false");
        props.setProperty("bedrock-bind", "0.0.0.0:19132");
        props.setProperty("bedrock-backend", "127.0.0.1:25566");
    }

    private void applyMissingDefaults() {
        Properties d = new Properties();
        LinkConfig tmp = new LinkConfig(home);
        tmp.applyDefaults();
        d.putAll(tmp.props);
        for (String key : d.stringPropertyNames()) {
            if (!props.containsKey(key)) {
                props.setProperty(key, d.getProperty(key));
            }
        }
    }

    public void save() throws IOException {
        Path file = home.resolve("link.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaP Link — first-party Velocity-class proxy");
        }
    }

    private void loadSecret() throws IOException {
        String mode = props.getProperty("player-info-forwarding-mode", "modern").trim().toLowerCase();
        if (!"modern".equals(mode)) {
            throw new IOException("YaP Link currently supports player-info-forwarding-mode=modern only");
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

    public Path home() {
        return home;
    }

    public String bindHost() {
        String bind = props.getProperty("bind", "0.0.0.0:25565").trim();
        int colon = bind.lastIndexOf(':');
        if (colon <= 0) {
            return "0.0.0.0";
        }
        return bind.substring(0, colon);
    }

    public int bindPort() {
        String bind = props.getProperty("bind", "0.0.0.0:25565").trim();
        int colon = bind.lastIndexOf(':');
        if (colon < 0 || colon == bind.length() - 1) {
            return 25565;
        }
        return Integer.parseInt(bind.substring(colon + 1).trim());
    }

    public String motd() {
        return props.getProperty("motd", "YaP Link");
    }

    public int maxPlayers() {
        try {
            return Integer.parseInt(props.getProperty("max-players", "500").trim());
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    public boolean onlineMode() {
        return Boolean.parseBoolean(props.getProperty("online-mode", "false"));
    }

    public byte[] forwardingSecret() {
        return forwardingSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String forwardingSecretString() {
        return forwardingSecret;
    }

    public Map<String, Backend> servers() {
        Map<String, Backend> map = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("servers.")) {
                continue;
            }
            String name = key.substring("servers.".length()).trim();
            if (name.isEmpty()) {
                continue;
            }
            map.put(name, Backend.parse(name, props.getProperty(key)));
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

    public Backend resolveServer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return servers().get(name.trim().toLowerCase());
    }

    /** Case-insensitive server lookup. */
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

    public boolean enableServerCommand() {
        return Boolean.parseBoolean(props.getProperty("enable-server-command", "true"));
    }

    public String publicHost() {
        String h = props.getProperty("public-host", "127.0.0.1").trim();
        return h.isEmpty() ? "127.0.0.1" : h;
    }

    public int publicPort() {
        try {
            int p = Integer.parseInt(props.getProperty("public-port", "0").trim());
            return p > 0 ? p : bindPort();
        } catch (NumberFormatException e) {
            return bindPort();
        }
    }

    public boolean bedrockEnabled() {
        return Boolean.parseBoolean(props.getProperty("bedrock-enabled", "false"));
    }

    public String bedrockBindHost() {
        return hostPart(props.getProperty("bedrock-bind", "0.0.0.0:19132"), "0.0.0.0");
    }

    public int bedrockBindPort() {
        return portPart(props.getProperty("bedrock-bind", "0.0.0.0:19132"), 19132);
    }

    public String bedrockBackendHost() {
        return hostPart(props.getProperty("bedrock-backend", "127.0.0.1:25566"), "127.0.0.1");
    }

    public int bedrockBackendPort() {
        return portPart(props.getProperty("bedrock-backend", "127.0.0.1:25566"), 25566);
    }

    private static String hostPart(String bind, String def) {
        if (bind == null || bind.isBlank()) {
            return def;
        }
        int colon = bind.lastIndexOf(':');
        return colon <= 0 ? bind.trim() : bind.substring(0, colon).trim();
    }

    private static int portPart(String bind, int def) {
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
            String host = v.substring(0, colon).trim();
            int port = Integer.parseInt(v.substring(colon + 1).trim());
            return new Backend(name, host, port);
        }
    }
}
