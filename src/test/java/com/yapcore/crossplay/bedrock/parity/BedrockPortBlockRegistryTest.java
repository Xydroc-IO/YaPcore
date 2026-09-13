package com.yapcore.crossplay.bedrock.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BedrockPortBlockRegistryTest {

    private static BedrockPortBlockRegistry registry;

    @BeforeAll
    static void load() {
        registry = BedrockPortBlockRegistry.loadDefault();
    }

    @Test
    void loadsAllCatalogBlocks() {
        assertEquals(24, registry.size());
        assertTrue(registry.byBedrockId("minecraft:allow").isPresent());
        assertTrue(registry.byJePortId("yapbedrock:allow").isPresent());
    }

    @Test
    void placementKindsMatchContract() {
        assertEquals(BedrockPortBlockRegistry.Placement.NATIVE_LIGHT,
                registry.byBedrockId("minecraft:light_block_7").orElseThrow().placement());
        assertEquals(7, registry.byBedrockId("minecraft:light_block_7").orElseThrow().lightLevel());
        assertEquals(BedrockPortBlockRegistry.Placement.NATIVE_STONECUTTER,
                registry.byBedrockId("minecraft:stonecutter_block").orElseThrow().placement());
        assertEquals(BedrockPortBlockRegistry.Placement.NATIVE_FRAME,
                registry.byBedrockId("minecraft:glow_frame").orElseThrow().placement());
        assertTrue(registry.byBedrockId("minecraft:glow_frame").orElseThrow().glowFrame());
        assertEquals(BedrockPortBlockRegistry.Placement.CARRIER_DISPLAY,
                registry.byBedrockId("minecraft:allow").orElseThrow().placement());
        assertEquals(BedrockPortBlockRegistry.Placement.CARRIER_DISPLAY,
                registry.byBedrockId("minecraft:compound_creator").orElseThrow().placement());
    }

    @Test
    void runtimeIndexRoundTrip() {
        var allow = registry.byBedrockId("minecraft:allow").orElseThrow();
        assertTrue(allow.bedrockRuntimeIndex() >= 0);
        assertEquals(allow, registry.byRuntimeIndex(allow.bedrockRuntimeIndex()).orElseThrow());
    }

    @Test
    void resolveAcceptsShortNames() {
        assertTrue(registry.resolve("allow").isPresent());
        assertTrue(registry.resolve("yapbedrock:deny").isPresent());
        assertTrue(registry.resolve("minecraft:border_block").isPresent());
    }
}
