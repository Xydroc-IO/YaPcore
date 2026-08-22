package com.yapcore.config.proxy;

import java.util.Properties;

/** Velocity modern forwarding settings ({@code velocity-*} keys). */
public final class VelocityProxyConfig {

    private final Properties props;

    public VelocityProxyConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("velocity-enabled", "false");
        props.setProperty("velocity-secret", "");
        props.setProperty("velocity-secret-file", "");
        props.setProperty("velocity-online-mode", "true");
        props.setProperty("velocity-bind-localhost", "true");
        props.setProperty("link-embed", "false");
        props.setProperty("link-embed-home", "link-data");
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

    /** When true, start native YaP Link in-process (dev / single-box). Requires yap-link.jar on classpath. */
    public boolean isLinkEmbed() {
        return Boolean.parseBoolean(props.getProperty("link-embed", "false"));
    }

    public void setLinkEmbed(boolean embed) {
        props.setProperty("link-embed", Boolean.toString(embed));
    }

    /** Home directory for embedded Link ({@code link.properties}, plugins, secrets). */
    public String getLinkEmbedHome() {
        return props.getProperty("link-embed-home", "link-data");
    }

    public void setLinkEmbedHome(String path) {
        props.setProperty("link-embed-home", path == null ? "link-data" : path.trim());
    }
}
