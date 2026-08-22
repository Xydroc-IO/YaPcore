package com.yapcore.config.authority;

import com.yapcore.config.ConfigSupport;
import com.yapcore.config.GameAuthority;
import com.yapcore.config.ServerConfig;

import java.util.Properties;

/** Folia game-authority settings ({@code folia-*} keys). */
public final class FoliaAuthorityConfig {

    private final ServerConfig config;
    private final Properties props;

    public FoliaAuthorityConfig(ServerConfig config, Properties props) {
        this.config = config;
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("folia-embed", "true");
        props.setProperty("folia-dir", "folia-kernel");
        props.setProperty("folia-port", "25567");
        props.setProperty("folia-version", "26.2");
        props.setProperty("folia-jar-url", "");
        props.setProperty("folia-ready-timeout-sec", "180");
    }

    public boolean isFoliaAuthority() {
        return config.getGameAuthority() == GameAuthority.FOLIA;
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
        return ConfigSupport.parseInt(props, "folia-port", 25567);
    }

    public String getFoliaVersion() {
        return props.getProperty("folia-version", "26.2");
    }

    public String getFoliaJarUrl() {
        return props.getProperty("folia-jar-url", "");
    }

    public int getFoliaReadyTimeoutSec() {
        return ConfigSupport.parseInt(props, "folia-ready-timeout-sec", 180);
    }

    /**
     * Port Folia binds. Via front + Folia authority → loopback {@link #getFoliaPort()};
     * otherwise public {@link ServerConfig#getPort()} when embed.
     */
    public int foliaListenPort() {
        if (isFoliaAuthority() && config.isProtocolViaEnabled()) {
            return getFoliaPort();
        }
        return isFoliaEmbed() ? config.getPort() : getFoliaPort();
    }

    /** True when Folia authority needs a JE TCP proxy to loopback. */
    public boolean isWrappedGameProxy() {
        return !isFoliaEmbed() || config.isProtocolViaEnabled();
    }

    /** True when YaPcore binds JE TCP for Folia authority. */
    public boolean isYaPcoreJavaListener() {
        return !isFoliaEmbed() || config.isProtocolViaEnabled();
    }
}
