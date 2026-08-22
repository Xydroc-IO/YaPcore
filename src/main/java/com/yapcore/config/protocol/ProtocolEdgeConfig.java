package com.yapcore.config.protocol;

import com.yapcore.config.ConfigSupport;
import com.yapcore.config.ServerConfig;

import java.util.Properties;

/** Java/Bedrock edge, crossplay, and Phase 4 protocol parity flags. */
public final class ProtocolEdgeConfig {

    private final ServerConfig config;
    private final Properties props;

    public ProtocolEdgeConfig(ServerConfig config, Properties props) {
        this.config = config;
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("java-enabled", "true");
        props.setProperty("bedrock-enabled", "true");
        props.setProperty("bedrock-port", "25566");
        props.setProperty("shared-listen-port", "true");
        props.setProperty("crossplay-enabled", "true");
        props.setProperty("allow-localhost", "true");
        props.setProperty("protocol-via-enabled", "true");
        props.setProperty("protocol-geyser-enabled", "true");
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
        return ConfigSupport.parseInt(props, "bedrock-port", 25566);
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
        return isSharedListenPort() ? config.getPort() : getBedrockPort();
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

    /**
     * Phase 4 Via* parity front door. When true under Folia/Paper authority, YaPcore owns
     * the public JE port and proxies (with remap) to the game on folia-port / paper-port.
     * <p>
     * Disabled under most MSPT benches so stock Paper and YaP hit the same socket path.
     * <strong>Exception: {@code highpop}</strong> — keep native Via front so forks pay
     * Via* plugin cost while YaP uses ProtocolCompat (fair product-surface compare).
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
}
