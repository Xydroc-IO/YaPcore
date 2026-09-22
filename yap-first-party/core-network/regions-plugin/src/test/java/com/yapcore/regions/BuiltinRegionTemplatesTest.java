package com.yapcore.regions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinRegionTemplatesTest {

    @Test
    void shipsTheCommonRegionTypes() {
        assertTrue(BuiltinRegionTemplates.names().contains("spawn"));
        assertTrue(BuiltinRegionTemplates.names().contains("hub"));
        assertTrue(BuiltinRegionTemplates.names().contains("wilderness"));
        assertTrue(BuiltinRegionTemplates.names().contains("arena"));
        assertTrue(BuiltinRegionTemplates.names().contains("creative"));
        assertTrue(BuiltinRegionTemplates.names().contains("market"));
    }

    @Test
    void spawnIsProtectedAndWildernessIsOpen() {
        BuiltinRegionTemplates.Preset spawn = BuiltinRegionTemplates.get("spawn");
        assertEquals("adventure", spawn.gameMode());
        assertEquals(FlagValue.DENY, spawn.flags().get(RegionFlag.PVP));
        assertEquals(FlagValue.DENY, spawn.flags().get(RegionFlag.BUILD));
        assertEquals("Welcome to spawn.", spawn.messages().get(RegionMessageKind.GREETING));

        BuiltinRegionTemplates.Preset wild = BuiltinRegionTemplates.get("wilderness");
        assertEquals("survival", wild.gameMode());
        assertTrue(wild.flags().isEmpty());
        assertNull(BuiltinRegionTemplates.get("not-a-template"));
    }
}
