package com.yapcore.plugincompat.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCompatUnitTest {

    @Test
    void versionedCraftPrefixesCover120And121() {
        var prefixes = PluginCompatTargets.versionedCraftPrefixes();
        assertTrue(prefixes.size() >= 8);
        assertTrue(prefixes.stream().anyMatch(p -> p.contains("v1_20_R3")));
        assertTrue(prefixes.stream().anyMatch(p -> p.contains("v1_21_R1")));
        assertEquals("1.20–1.21", PluginCompatTargets.SOURCE_RANGE);
        assertEquals("26.2", PluginCompatTargets.TARGET_PAPER);
        assertEquals(".yap-plugin-compat-backup", PluginCompatTargets.BACKUP_DIR);
    }

    @Test
    void rewriteStripsCraftBukkitVersionSegment() {
        assertEquals(
                "org/bukkit/craftbukkit/entity/CraftPlayer",
                PluginCompatTargets.rewriteInternalName(
                        "org/bukkit/craftbukkit/v1_20_R3/entity/CraftPlayer"));
        assertEquals(
                "org/bukkit/craftbukkit/CraftServer",
                PluginCompatTargets.rewriteInternalName(
                        "org/bukkit/craftbukkit/v1_21_R5/CraftServer"));
        assertEquals("java/lang/String", PluginCompatTargets.rewriteInternalName("java/lang/String"));
        assertEquals(null, PluginCompatTargets.rewriteInternalName(null));
    }

    @Test
    void pluginYmlAndMainClassLoad() throws Exception {
        assertEquals(YapPluginCompatPlugin.class,
                Class.forName("com.yapcore.plugincompat.paper.YapPluginCompatPlugin"));
        try (InputStream in = YapPluginCompatPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("name: YaPPluginCompat"));
            assertTrue(yml.contains("main: com.yapcore.plugincompat.paper.YapPluginCompatPlugin"));
            assertTrue(yml.contains("yapcompat"));
            assertFalse(yml.contains("depend:"));
        }
    }
}
