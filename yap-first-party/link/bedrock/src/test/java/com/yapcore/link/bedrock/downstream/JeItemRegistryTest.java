package com.yapcore.link.bedrock.downstream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeItemRegistryTest {

    @Test
    void dirtAndOakLogMapByIdentifier() {
        assertTrue(JeItemRegistry.size() > 100, "item table loaded");
        assertEquals("minecraft:dirt", JeItemRegistry.name(JeItemRegistry.id("minecraft:dirt")));
        assertEquals("minecraft:oak_log", JeItemRegistry.name(JeItemRegistry.id("minecraft:oak_log")));
        assertEquals(55, JeItemRegistry.id("minecraft:dirt"));
        assertEquals(161, JeItemRegistry.id("minecraft:oak_log"));
        assertEquals(55, JeItemRegistry.id("dirt"));
    }
}
