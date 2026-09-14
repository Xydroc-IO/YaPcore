package com.yapcore.config.authority;

import com.yapcore.config.ConfigSupport;

import java.util.Properties;

/**
 * Internal physics sub-steps ({@code folia-physics-*} / {@code -Dyap.folia.physics-substeps}).
 *
 * <p>Higher-rate movement/combat integration inside one logical Folia tick (plugin tick stays
 * 20 TPS). Patch {@code 0031-yap-physics-substeps}. Ship default <strong>on</strong>.
 */
public final class FoliaPhysicsSubstepConfig {

    private final Properties props;

    public FoliaPhysicsSubstepConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("folia-physics-substeps", "true");
        props.setProperty("folia-physics-substep-count", "4");
        props.setProperty("folia-physics-substep-min-move", "0.02");
    }

    /** {@code -Dyap.folia.physics-substeps}. */
    public boolean isPhysicsSubsteps() {
        return Boolean.parseBoolean(props.getProperty("folia-physics-substeps", "false"));
    }

    /** Substep count 2–8 ({@code -Dyap.folia.physics-substep-count}). Default 4. */
    public int getPhysicsSubstepCount() {
        int n = ConfigSupport.parseInt(props, "folia-physics-substep-count", 4);
        return Math.max(2, Math.min(8, n));
    }

    /** Min |delta| to subdivide moves ({@code -Dyap.folia.physics-substep-min-move}). */
    public double getPhysicsSubstepMinMove() {
        return ConfigSupport.parseDouble(props, "folia-physics-substep-min-move", 0.02);
    }
}
