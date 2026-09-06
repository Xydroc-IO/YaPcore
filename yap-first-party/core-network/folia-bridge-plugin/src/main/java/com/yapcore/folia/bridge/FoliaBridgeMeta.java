package com.yapcore.folia.bridge;

/**
 * Pure metadata / Folia-detection helpers for the Folia bridge surface.
 * Kept package-visible so smoke tests can assert without a live server.
 */
final class FoliaBridgeMeta {

    static final String PLUGIN_NAME = "YaPFoliaBridge";
    static final String MAIN_CLASS = "com.yapcore.folia.bridge.FoliaBridgePlugin";
    static final String STATUS_COMMAND = "yapbridge";
    static final String FOLIA_REGION_HINT = "io.papermc.paper.threadedregions";

    private FoliaBridgeMeta() {
    }

    /** Heuristic: class/package names that imply Folia's regionized scheduler. */
    static boolean looksLikeFoliaType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        String n = typeName.trim();
        return n.contains(FOLIA_REGION_HINT)
                || n.contains("GlobalRegionScheduler")
                || n.contains("RegionScheduler")
                || n.contains(".folia.");
    }
}
