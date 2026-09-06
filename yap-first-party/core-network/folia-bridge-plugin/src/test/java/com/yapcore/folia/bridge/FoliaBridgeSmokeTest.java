package com.yapcore.folia.bridge;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaBridgeSmokeTest {

    @Test
    void metaConstantsMatchPluginSurface() {
        assertEquals("YaPFoliaBridge", FoliaBridgeMeta.PLUGIN_NAME);
        assertEquals("yapbridge", FoliaBridgeMeta.STATUS_COMMAND);
        assertEquals(FoliaBridgePlugin.class.getName(), FoliaBridgeMeta.MAIN_CLASS);
        assertTrue(FoliaBridgeMeta.FOLIA_REGION_HINT.contains("threadedregions"));
    }

    @Test
    void foliaTypeHeuristic() {
        assertTrue(FoliaBridgeMeta.looksLikeFoliaType(
                "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler"));
        assertTrue(FoliaBridgeMeta.looksLikeFoliaType("com.yapcore.folia.bridge.FoliaBridgePlugin"));
        assertTrue(FoliaBridgeMeta.looksLikeFoliaType("RegionScheduler"));
        assertFalse(FoliaBridgeMeta.looksLikeFoliaType("org.bukkit.Bukkit"));
        assertFalse(FoliaBridgeMeta.looksLikeFoliaType(null));
        assertFalse(FoliaBridgeMeta.looksLikeFoliaType("  "));
    }

    @Test
    void pluginYmlAndMainClassLoad() throws Exception {
        assertEquals(FoliaBridgePlugin.class, Class.forName(FoliaBridgeMeta.MAIN_CLASS));
        try (InputStream in = FoliaBridgePlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("name: " + FoliaBridgeMeta.PLUGIN_NAME));
            assertTrue(yml.contains("main: " + FoliaBridgeMeta.MAIN_CLASS));
            assertTrue(yml.contains("folia-supported: true"));
            assertTrue(yml.contains(FoliaBridgeMeta.STATUS_COMMAND));
        }
    }
}
