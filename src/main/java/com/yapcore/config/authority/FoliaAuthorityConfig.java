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
        props.setProperty("folia-scoreboard-swmr", "true");
        // Phase 4 region pool / micro-tick — defaults OFF (safe)
        props.setProperty("folia-microtick-budget-ms", "0");
        props.setProperty("folia-steal-threshold-ms", "3");
        props.setProperty("folia-task-slice-ms", "2");
        props.setProperty("folia-grid-exponent", "");
        props.setProperty("folia-region-metrics", "true");
        // Phase 5 — true parallel sub-regions via force-partition (default OFF)
        props.setProperty("folia-subregion-partition", "false");
        props.setProperty("folia-subregion-shards", "2");
        props.setProperty("folia-subregion-mspt-threshold", "20");
        props.setProperty("folia-subregion-min-sections", "4");
        props.setProperty("folia-subregion-min-entities", "32");
        props.setProperty("folia-subregion-coalesce-mspt", "8");
        props.setProperty("folia-subregion-coalesce-ticks", "100");
        props.setProperty("folia-subregion-coalesce-quiet-ticks", "200");
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
     * Soft deadline (ms) for Mob AI phase per region tick ({@code -Dyap.folia.microtick-budget-ms}).
     * {@code 0} = off. Same-thread deferral — not true parallel sub-regions.
     */
    public int getFoliaMicrotickBudgetMs() {
        return ConfigSupport.parseInt(props, "folia-microtick-budget-ms", 0);
    }

    /** WORK_STEALING steal threshold ms ({@code -Dyap.folia.steal-threshold-ms}). Default 3. */
    public long getFoliaStealThresholdMs() {
        return ConfigSupport.parseLong(props, "folia-steal-threshold-ms", 3L);
    }

    /** WORK_STEALING task slice ms ({@code -Dyap.folia.task-slice-ms}). Default 2. */
    public long getFoliaTaskSliceMs() {
        return ConfigSupport.parseLong(props, "folia-task-slice-ms", 2L);
    }

    /**
     * Optional override for Folia {@code threaded-regions.grid-exponent}
     * ({@code -Dyap.folia.grid-exponent}). Empty = use paper-global.yml.
     */
    public String getFoliaGridExponent() {
        return props.getProperty("folia-grid-exponent", "").trim();
    }

    /** Region merge/split/migration counters ({@code -Dyap.folia.region-metrics}). Default on. */
    public boolean isFoliaRegionMetrics() {
        return Boolean.parseBoolean(props.getProperty("folia-region-metrics", "true"));
    }

    /**
     * Force-partition hot regions into independent Folia shards that tick in parallel
     * ({@code -Dyap.folia.subregion-partition}). Default off.
     */
    public boolean isFoliaSubregionPartition() {
        return Boolean.parseBoolean(props.getProperty("folia-subregion-partition", "false"));
    }

    public int getFoliaSubregionShards() {
        return ConfigSupport.parseInt(props, "folia-subregion-shards", 2);
    }

    public int getFoliaSubregionMsptThreshold() {
        return ConfigSupport.parseInt(props, "folia-subregion-mspt-threshold", 20);
    }

    public int getFoliaSubregionMinSections() {
        return ConfigSupport.parseInt(props, "folia-subregion-min-sections", 4);
    }

    public int getFoliaSubregionMinEntities() {
        return ConfigSupport.parseInt(props, "folia-subregion-min-entities", 32);
    }

    public int getFoliaSubregionCoalesceMspt() {
        return ConfigSupport.parseInt(props, "folia-subregion-coalesce-mspt", 8);
    }

    public int getFoliaSubregionCoalesceTicks() {
        return ConfigSupport.parseInt(props, "folia-subregion-coalesce-ticks", 100);
    }

    public int getFoliaSubregionCoalesceQuietTicks() {
        return ConfigSupport.parseInt(props, "folia-subregion-coalesce-quiet-ticks", 200);
    }

    /** Corridor unload before force-partition ({@code -Dyap.folia.subregion-carve}). Default true. */
    public boolean isFoliaSubregionCarve() {
        return Boolean.parseBoolean(props.getProperty("folia-subregion-carve", "true"));
    }

    public int getFoliaSubregionPartitionDelayTicks() {
        return ConfigSupport.parseInt(props, "folia-subregion-partition-delay-ticks", 600);
    }

    public int getFoliaSubregionGapMaintainInterval() {
        return ConfigSupport.parseInt(props, "folia-subregion-gap-maintain-interval", 10);
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
