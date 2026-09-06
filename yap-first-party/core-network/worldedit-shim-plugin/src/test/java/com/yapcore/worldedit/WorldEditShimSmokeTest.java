package com.yapcore.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditShimSmokeTest {

    @Test
    void pluginYmlUsesWorldEditName() throws Exception {
        try (InputStream in = WorldEditShimPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("name: WorldEdit"));
            assertTrue(yml.contains("main: com.yapcore.worldedit.WorldEditShimPlugin"));
            assertTrue(yml.contains("depend: [YaPWorld]"));
            assertTrue(yml.contains("api-version: '1.21'"));
            assertTrue(yml.contains("folia-supported: true"));
        }
    }

    @Test
    void facadeTypesPresentOnClasspath() throws Exception {
        assertEquals(WorldEditShimPlugin.class,
                Class.forName("com.yapcore.worldedit.WorldEditShimPlugin"));
        assertNotNull(Class.forName("com.sk89q.worldedit.WorldEdit"));
        assertNotNull(Class.forName("com.sk89q.worldedit.LocalSession"));
        assertNotNull(Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat"));
        assertNotNull(Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter"));
    }

    @Test
    void worldEditSingletonAndRegionMath() {
        WorldEdit we = WorldEdit.getInstance();
        assertNotNull(we);
        assertSame(we, WorldEdit.getInstance());
        assertNotNull(we.getSessionManager());
        assertNotNull(ClipboardFormat.SCHEMATIC);

        BlockVector3 min = BlockVector3.at(0, 64, 0);
        BlockVector3 max = BlockVector3.at(15, 80, 15);
        CuboidRegion region = new CuboidRegion(min, max);
        assertEquals(0, region.getMinimumPoint().x());
        assertEquals(64, region.getMinimumPoint().y());
        assertEquals(15, region.getMaximumPoint().x());
        assertEquals(80, region.getMaximumPoint().y());
        assertEquals(16L * 17L * 16L, region.getVolume());
    }
}
