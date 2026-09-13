package com.yapcore.bedrockblocks;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortBlockCatalogTest {

    private static PortBlockCatalog catalog;

    @BeforeAll
    static void load() {
        catalog = PortBlockCatalog.loadFromClasspath();
    }

    @Test
    void catalogLoadsTwentyFour() {
        assertEquals(24, catalog.size());
        assertEquals(24, catalog.all().size());
    }

    @Test
    void placementKindsForLightStonecutterFrameCarrier() {
        assertEquals(PlacementKind.NATIVE_LIGHT,
                catalog.resolve("light_block_0").orElseThrow().placement());
        assertEquals(PlacementKind.NATIVE_LIGHT,
                catalog.resolve("minecraft:light_block_7").orElseThrow().placement());
        assertEquals(15, catalog.resolve("light_block_15").orElseThrow().lightLevel());

        assertEquals(PlacementKind.NATIVE_STONECUTTER,
                catalog.resolve("stonecutter_block").orElseThrow().placement());

        assertEquals(PlacementKind.NATIVE_FRAME,
                catalog.resolve("frame").orElseThrow().placement());
        assertEquals(PlacementKind.NATIVE_FRAME,
                catalog.resolve("glow_frame").orElseThrow().placement());
        assertTrue(catalog.resolve("glow_frame").orElseThrow().glowFrame());

        assertEquals(PlacementKind.CARRIER_DISPLAY,
                catalog.resolve("allow").orElseThrow().placement());
        assertEquals(PlacementKind.CARRIER_DISPLAY,
                catalog.resolve("yapbedrock:compound_creator").orElseThrow().placement());
        assertEquals(PlacementKind.CARRIER_DISPLAY,
                catalog.resolve("hard_glass_pane").orElseThrow().placement());
    }

    @Test
    void placementHelperMatchesChassisRules() {
        assertEquals(PlacementKind.NATIVE_LIGHT, PortBlockCatalog.placementFor("minecraft:light_block_0"));
        assertEquals(PlacementKind.NATIVE_STONECUTTER, PortBlockCatalog.placementFor("minecraft:stonecutter_block"));
        assertEquals(PlacementKind.NATIVE_FRAME, PortBlockCatalog.placementFor("minecraft:frame"));
        assertEquals(PlacementKind.CARRIER_DISPLAY, PortBlockCatalog.placementFor("minecraft:allow"));
    }

    @Test
    void resourceJsonParsable() {
        try (InputStream in = PortBlockCatalogTest.class.getClassLoader()
                .getResourceAsStream("parity/band_26_50/blocks.v1.json")) {
            assertTrue(in != null);
            assertTrue(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("entries")
                    .size() == 24);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
