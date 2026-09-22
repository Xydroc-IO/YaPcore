package com.yapcore.link;

import java.util.Properties;

/** Default {@code link.properties} seeds (split from {@link LinkConfig}). */
final class LinkConfigDefaults {

    private LinkConfigDefaults() {}

    static void applyDefaults(Properties props) {
        props.setProperty("bind", "0.0.0.0:25565");
        props.setProperty("motd", "YaP Link");
        props.setProperty("max-players", "500");
        // Prefer offline + Mojang UUID/skin rewrite; online-mode encryption is brittle on Link.
        props.setProperty("online-mode", "false");
        props.setProperty("player-info-forwarding-mode", "modern");
        props.setProperty("forwarding-secret-file", "forwarding.secret");
        props.setProperty("show-ping-requests", "false");
        props.setProperty("servers.lobby", "127.0.0.1:25566");
        props.setProperty("try", "lobby");
        props.setProperty("force-default-server", "true");
        // When survival/creative/etc dies mid-session, soft-switch players to hub (try[0]).
        props.setProperty("fallback-on-backend-loss", "true");
        // Empty = first entry of try (usually lobby). Override if hub id differs.
        props.setProperty("fallback-server", "");
        props.setProperty("enable-server-command", "true");
        props.setProperty("public-host", "127.0.0.1");
        props.setProperty("public-port", "0");
        props.setProperty("bedrock-enabled", "true");
        props.setProperty("bedrock-mode", "native");
        props.setProperty("bedrock-bind", "0.0.0.0:19132");
        props.setProperty("bedrock-backend", "127.0.0.1:25566");
        props.setProperty("geyser-enabled", "false");
        props.setProperty("geyser-home", "geyser");
        props.setProperty("geyser-jar", "Geyser-Standalone.jar");
        props.setProperty("geyser-remote-host", "127.0.0.1");
        props.setProperty("geyser-remote-port", "25565");
        props.setProperty("geyser-bedrock-bind", "0.0.0.0:19132");
        // Unconnected Pong MOTD (phone list ping) — Geyser/BDS field order
        // Match current Bedrock release (26.45 = proto 2169). Beta 2207 breaks list ping UX.
        props.setProperty("bedrock-motd-protocol", "2169");
        props.setProperty("bedrock-motd-version", "26.45");
        props.setProperty("bedrock-motd-sub", "YaP Link");
        // Phase 1
        props.setProperty("ping-passthrough", "true");
        props.setProperty("backend-probe-interval-sec", "10");
        props.setProperty("backend-probe-timeout-ms", "3000");
        props.setProperty("connect-timeout-ms", "10000");
        props.setProperty("login-timeout-ms", "30000");
        props.setProperty("read-timeout-sec", "300");
        props.setProperty("skip-down-on-forced-host", "false");
        // Phase 2
        props.setProperty("aggregate-player-count", "true");
        props.setProperty("global-tab-list", "false");
        props.setProperty("chat-relay-enabled", "true");
        props.setProperty("chat-relay-channel", "network");
        props.setProperty("chat-relay-format", "[{server}] {name}: {message}");
        props.setProperty("chat-join-announce", "false");
        // Phase 3+ — default OFF in code; first run / release seed sets plugins-enabled=true
        props.setProperty("plugins-enabled", "false");
        props.setProperty("floodgate-key-file", "floodgate-key.pem");
        // Phase 0 — edge rate limits (defaults ON; loopback exempt)
        props.setProperty("connect-rate-limit-enabled", "true");
        props.setProperty("connect-rate-per-ip", "20");
        props.setProperty("connect-rate-window-ms", "10000");
        props.setProperty("handshake-rate-limit-enabled", "true");
        props.setProperty("handshake-rate-per-ip", "40");
        props.setProperty("handshake-rate-window-ms", "10000");
        props.setProperty("login-rate-limit-enabled", "true");
        props.setProperty("login-rate-per-ip", "10");
        props.setProperty("login-rate-window-ms", "10000");
        props.setProperty("rate-limit-exempt-loopback", "true");
        props.setProperty("max-concurrent-per-ip-enabled", "true");
        props.setProperty("max-concurrent-per-ip", "8");
        props.setProperty("metrics-http-enabled", "true");
        props.setProperty("metrics-http-bind", "127.0.0.1");
        props.setProperty("metrics-http-port", "9091");
    }
}
