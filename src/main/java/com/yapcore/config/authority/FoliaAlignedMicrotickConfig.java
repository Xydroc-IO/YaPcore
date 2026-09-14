package com.yapcore.config.authority;

import com.yapcore.config.ConfigSupport;

import java.util.Properties;

/**
 * Aligned cross-region micro/sub-tick knobs ({@code folia-aligned-*} /
 * {@code -Dyap.folia.aligned-microticks}).
 *
 * <p>Ship default <strong>on</strong> (patches 0026–0030, universal RTQ tagging).
 */
public final class FoliaAlignedMicrotickConfig {

    private final Properties props;

    public FoliaAlignedMicrotickConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        // Ship-on after smoke PASS with universal RTQ tagging (0030).
        props.setProperty("folia-aligned-microticks", "true");
        props.setProperty("folia-micro-phases", "4");
        props.setProperty("folia-tick-wave-max-wait-ms", "2");
    }

    /** Real micro/sub-tick phases + soft cross-region waves ({@code -Dyap.folia.aligned-microticks}). */
    public boolean isAlignedMicroticks() {
        return Boolean.parseBoolean(props.getProperty("folia-aligned-microticks", "false"));
    }

    /** Phase count 2–4 ({@code -Dyap.folia.micro-phases}). Default 4. */
    public int getMicroPhases() {
        int n = ConfigSupport.parseInt(props, "folia-micro-phases", 4);
        return Math.max(2, Math.min(4, n));
    }

    /** Soft barrier max wait ms; must be &gt; 0 ({@code -Dyap.folia.tick-wave-max-wait-ms}). */
    public int getTickWaveMaxWaitMs() {
        return Math.max(1, ConfigSupport.parseInt(props, "folia-tick-wave-max-wait-ms", 2));
    }
}
