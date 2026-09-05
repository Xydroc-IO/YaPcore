package com.yapcore.knobs;

/**
 * Bridge to YaP-Folia encyclopedia NMS hooks (E2).
 * Plugin writes system properties; Folia patch {@code YapEncyclopediaHooks} reads them.
 * Defaults are vanilla-safe (modifiers = 1.0 / fluids on).
 */
public final class EncyclopediaNms {

    public static final String PROP_CROP = "yap.encyclopedia.crop-growth-modifier";
    public static final String PROP_CROP_NMS = "yap.encyclopedia.crop-growth-nms";
    public static final String PROP_FLUIDS = "yap.encyclopedia.tick-fluids";
    public static final String PROP_BRAND = "yap.encyclopedia.server-mod-name";

    private EncyclopediaNms() {
    }

    public static void syncFromConfig(KnobsConfig config) {
        if (config == null) {
            clear();
            return;
        }
        KnobsConfig.GameplaySettings g = config.gameplay();
        System.setProperty(PROP_CROP, Double.toString(g.cropGrowthModifier()));
        System.setProperty(PROP_CROP_NMS, Boolean.toString(g.cropGrowthNms()));
        System.setProperty(PROP_FLUIDS, Boolean.toString(g.tickFluids()));
        if (config.serverModName() != null) {
            System.setProperty(PROP_BRAND, config.serverModName());
        }
    }

    public static void clear() {
        System.clearProperty(PROP_CROP);
        System.clearProperty(PROP_CROP_NMS);
        System.clearProperty(PROP_FLUIDS);
    }

    public static boolean hooksPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.YapEncyclopediaHooks");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * True when knobs ask for NMS crop/fluid behavior that needs YapEncyclopediaHooks.
     */
    public static boolean configNeedsHooks(KnobsConfig config) {
        if (config == null) {
            return false;
        }
        KnobsConfig.GameplaySettings g = config.gameplay();
        return g.cropGrowthNms() || !g.tickFluids();
    }

    /**
     * Log + status helper when NMS knobs are on but Folia patch 0025 is missing.
     */
    public static void warnIfMisconfigured(java.util.logging.Logger log, KnobsConfig config) {
        if (!configNeedsHooks(config)) {
            return;
        }
        if (hooksPresent()) {
            return;
        }
        log.warning("Encyclopedia NMS knobs enabled (crop-growth-nms and/or tick-fluids=false) "
                + "but YapEncyclopediaHooks is absent — rebuild YaP-Folia with "
                + "0025-yap-encyclopedia-hooks.patch (./scripts/build-yap-folia.sh). "
                + "Event-wired knobs still work; NMS crop/fluid gates will not.");
    }

    public static String statusLine() {
        boolean present = hooksPresent();
        String base = "present=" + present
                + " crop=" + System.getProperty(PROP_CROP, "1.0")
                + " cropNms=" + System.getProperty(PROP_CROP_NMS, "false")
                + " fluids=" + System.getProperty(PROP_FLUIDS, "true");
        if (!present) {
            return base + " — rebuild YaP-Folia with 0025 for crop/fluid NMS";
        }
        return base;
    }
}
