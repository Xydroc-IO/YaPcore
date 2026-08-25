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
        props.setProperty("folia-jar-source", "build");
        props.setProperty("folia-jar-path", "");
        props.setProperty("folia-ready-timeout-sec", "180");
        props.setProperty("folia-sched-compat", "true");
        props.setProperty("folia-sched-compat-warn", "true");
        props.setProperty("folia-teleport-transactions", "true");
        // Phase 3 perf knobs — product defaults OFF (safe). Enable for soak-perf / hot spawn.
        props.setProperty("folia-async-chunk-save", "false");
        props.setProperty("folia-entity-tick-budget", "0");
        props.setProperty("folia-scoreboard-swmr", "false");
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

    /**
     * Jar resolution mode: {@code build} (prefer {@code lib/yap-folia-*}),
     * {@code fetch} (Fill / folia-jar-url), {@code path} (folia-jar-path only),
     * or {@code auto} (yap-folia if present, else cache/Fill).
     */
    public String getFoliaJarSource() {
        return props.getProperty("folia-jar-source", "build");
    }

    public String getFoliaJarPath() {
        return props.getProperty("folia-jar-path", "");
    }

    public int getFoliaReadyTimeoutSec() {
        return ConfigSupport.parseInt(props, "folia-ready-timeout-sec", 180);
    }

    public boolean isFoliaSchedCompat() {
        return Boolean.parseBoolean(props.getProperty("folia-sched-compat", "true"));
    }

    public void setFoliaSchedCompat(boolean enabled) {
        props.setProperty("folia-sched-compat", Boolean.toString(enabled));
    }

    public boolean isFoliaSchedCompatWarn() {
        return Boolean.parseBoolean(props.getProperty("folia-sched-compat-warn", "true"));
    }

    public boolean isFoliaTeleportTransactions() {
        return Boolean.parseBoolean(props.getProperty("folia-teleport-transactions", "true"));
    }

    /** Offload Moonrise flush off region thread ({@code -Dyap.folia.async-chunk-save}). Default off. */
    public boolean isFoliaAsyncChunkSave() {
        return Boolean.parseBoolean(props.getProperty("folia-async-chunk-save", "false"));
    }

    /**
     * Max Mob AI ticks per region tick ({@code -Dyap.folia.entity-tick-budget}).
     * {@code 0} = off. TNT/players/vehicles always tick.
     */
    public int getFoliaEntityTickBudget() {
        return ConfigSupport.parseInt(props, "folia-entity-tick-budget", 0);
    }

    /** Allow Bukkit scoreboard mutations under SWMR ({@code -Dyap.folia.scoreboard-swmr}). Default off. */
    public boolean isFoliaScoreboardSwmr() {
        return Boolean.parseBoolean(props.getProperty("folia-scoreboard-swmr", "false"));
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
