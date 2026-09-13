package com.yapcore.link;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Bedrock / Geyser / Floodgate config accessors (split from {@link LinkConfig}).
 */
final class LinkConfigBedrock {

    private LinkConfigBedrock() {}

    /**
     * Bedrock path: {@code native} | {@code forwarder} | {@code geyser-backup}.
     * Legacy {@code first-party} maps to {@code forwarder}.
     */
    static String bedrockMode(LinkConfig cfg) {
        String m = cfg.props.getProperty("bedrock-mode", "native").trim().toLowerCase(Locale.ROOT);
        if ("native".equals(m)) {
            return "native";
        }
        if ("geyser-backup".equals(m) || "geyser".equals(m) || "backup".equals(m)) {
            return "geyser-backup";
        }
        // first-party (legacy), forwarder, or unknown → forwarder
        return "forwarder";
    }

    /** True when Link owns Bedrock sessions ({@link com.yapcore.link.bedrock.session.BedrockSessionHost}). */
    static boolean bedrockNativeEnabled(LinkConfig cfg) {
        // Missing key must default ON for native — otherwise no UDP listener → "loading ping".
        return "native".equals(bedrockMode(cfg)) && cfg.bool("bedrock-enabled", true);
    }

    /** True when Link should run {@code BedrockUdpForwarder} (forwarder mode only). */
    static boolean bedrockEnabled(LinkConfig cfg) {
        if (!"forwarder".equals(bedrockMode(cfg))) {
            return false;
        }
        return cfg.bool("bedrock-enabled", false);
    }

    static boolean geyserEnabled(LinkConfig cfg) {
        return "geyser-backup".equals(bedrockMode(cfg)) || cfg.bool("geyser-enabled", false);
    }

    static String geyserHomeRel(LinkConfig cfg) {
        return cfg.props.getProperty("geyser-home", "geyser").trim();
    }

    static String geyserJarName(LinkConfig cfg) {
        return cfg.props.getProperty("geyser-jar", "Geyser-Standalone.jar").trim();
    }

    static String geyserRemoteHost(LinkConfig cfg) {
        return cfg.props.getProperty("geyser-remote-host", "127.0.0.1").trim();
    }

    static int geyserRemotePort(LinkConfig cfg) {
        return cfg.intProp("geyser-remote-port", 25565);
    }

    static String geyserBedrockBind(LinkConfig cfg) {
        return cfg.props.getProperty("geyser-bedrock-bind", "0.0.0.0:19132").trim();
    }

    static String bedrockBindHost(LinkConfig cfg) {
        return bedrockBindAddresses(cfg).get(0).getHostString();
    }

    static int bedrockBindPort(LinkConfig cfg) {
        return bedrockBindAddresses(cfg).get(0).getPort();
    }

    /**
     * UDP edge binds from {@code bedrock-bind} (comma-separated).
     * <p>
     * Defaults are dedicated Bedrock ({@code :19132} only). Shared-port JE/BE on
     * {@code :25565} is opt-in via {@code bedrock-shared-port=true} (and optional
     * {@code bedrock-also-19132=true}). Do not silently re-add ports — that made
     * operator “dedicated 19132” configs look broken.
     */
    static java.util.List<java.net.InetSocketAddress> bedrockBindAddresses(LinkConfig cfg) {
        String raw = cfg.props.getProperty("bedrock-bind", "0.0.0.0:19132");
        java.util.LinkedHashSet<String> specs = new java.util.LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    specs.add(t);
                }
            }
        }
        if (specs.isEmpty()) {
            specs.add("0.0.0.0:19132");
        }
        // Opt-in union only — never default-on (stomps dedicated :19132).
        if (cfg.bindPort() == 25565 && cfg.bool("bedrock-shared-port", false)) {
            String edgeHost = cfg.bindHost().isBlank() ? "0.0.0.0" : cfg.bindHost();
            specs.add(edgeHost + ":25565");
            if (cfg.bool("bedrock-also-19132", false)) {
                specs.add(edgeHost + ":19132");
            }
        }
        java.util.ArrayList<java.net.InetSocketAddress> out = new java.util.ArrayList<>(specs.size());
        for (String spec : specs) {
            String host = LinkConfig.hostPart(spec, "0.0.0.0");
            int port = LinkConfig.portPart(spec, 19132);
            try {
                out.add(new java.net.InetSocketAddress(java.net.InetAddress.getByName(host), port));
            } catch (java.net.UnknownHostException e) {
                throw new IllegalArgumentException("Invalid bedrock-bind host: " + host, e);
            }
        }
        return java.util.List.copyOf(out);
    }

    static String bedrockBackendHost(LinkConfig cfg) {
        return LinkConfig.hostPart(cfg.props.getProperty("bedrock-backend", "127.0.0.1:25566"), "127.0.0.1");
    }

    static int bedrockBackendPort(LinkConfig cfg) {
        return LinkConfig.portPart(cfg.props.getProperty("bedrock-backend", "127.0.0.1:25566"), 25566);
    }

    /** Protocol id advertised in Bedrock Unconnected Pong MOTD (phone list ping). */
    static int bedrockMotdProtocol(LinkConfig cfg) {
        return cfg.intProp("bedrock-motd-protocol", 2169);
    }

    /** Version name advertised in Bedrock Unconnected Pong MOTD. */
    static String bedrockMotdVersion(LinkConfig cfg) {
        return cfg.props.getProperty("bedrock-motd-version", "26.45");
    }

    /** Secondary MOTD / level name in Unconnected Pong. */
    static String bedrockMotdSub(LinkConfig cfg) {
        return cfg.props.getProperty("bedrock-motd-sub", "YaP Link");
    }

    static boolean pluginsEnabled(LinkConfig cfg) {
        return cfg.bool("plugins-enabled", false);
    }

    static Path floodgateKeyFile(LinkConfig cfg) {
        return cfg.home.resolve(cfg.props.getProperty("floodgate-key-file", "floodgate-key.pem").trim());
    }

    /** Per-backend Bedrock Geyser target; falls back to global {@code bedrock-backend}. */
    static LinkConfig.BedrockTarget bedrockBackendFor(LinkConfig cfg, String serverName) {
        String key = "servers." + serverName + ".bedrock";
        String raw = cfg.props.getProperty(key);
        if (raw != null && !raw.isBlank()) {
            return LinkConfig.BedrockTarget.parse(raw);
        }
        return new LinkConfig.BedrockTarget(bedrockBackendHost(cfg), bedrockBackendPort(cfg));
    }

}
