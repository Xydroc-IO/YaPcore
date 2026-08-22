package com.yapcore.config.authority;

import com.yapcore.config.ConfigSupport;
import com.yapcore.config.GameAuthority;
import com.yapcore.config.ServerConfig;

import java.util.Properties;

/** Paper game-authority settings ({@code paper-*} keys). */
public final class PaperAuthorityConfig {

    private final ServerConfig config;
    private final Properties props;

    public PaperAuthorityConfig(ServerConfig config, Properties props) {
        this.config = config;
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("paper-embed", "true");
        props.setProperty("paper-same-jvm", "false");
        props.setProperty("paper-dir", "paper-kernel");
        props.setProperty("paper-port", "25567");
        props.setProperty("paper-version", "26.2");
        props.setProperty("paper-jar-url", "");
        props.setProperty("paper-ready-timeout-sec", "180");
    }

    public boolean isPaperAuthority() {
        return config.getGameAuthority() == GameAuthority.PAPER;
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

    /**
     * Port Paper actually binds. With Via front + Paper authority → loopback
     * {@link #getPaperPort()}; otherwise public {@link ServerConfig#getPort()} when embed.
     */
    public int paperListenPort() {
        if (isPaperAuthority() && config.isProtocolViaEnabled()) {
            return getPaperPort();
        }
        return isPaperEmbed() ? config.getPort() : getPaperPort();
    }

    public String getPaperDir() {
        String override = System.getProperty("yapcore.paper.dir");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return props.getProperty("paper-dir", "paper-kernel");
    }

    public int getPaperPort() {
        return ConfigSupport.parseInt(props, "paper-port", 25567);
    }

    public String getPaperVersion() {
        return props.getProperty("paper-version", "26.2");
    }

    public String getPaperJarUrl() {
        return props.getProperty("paper-jar-url", "");
    }

    public int getPaperReadyTimeoutSec() {
        return ConfigSupport.parseInt(props, "paper-ready-timeout-sec", 180);
    }

    /** True when Paper authority needs a JE TCP proxy to loopback. */
    public boolean isWrappedGameProxy() {
        return !isPaperEmbed() || config.isProtocolViaEnabled();
    }

    /** True when YaPcore binds JE TCP for Paper authority. */
    public boolean isYaPcoreJavaListener() {
        return !isPaperEmbed() || config.isProtocolViaEnabled();
    }
}
