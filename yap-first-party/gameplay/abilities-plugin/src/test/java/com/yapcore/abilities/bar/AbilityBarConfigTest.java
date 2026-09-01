package com.yapcore.abilities.bar;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityBarConfigTest {

    @Test
    void mapsKeysFourThroughNine() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ability-bar.first-key", 4);
        yaml.set("ability-bar.slot-count", 6);
        AbilityBarConfig cfg = new AbilityBarConfig(yaml);
        assertEquals(4, cfg.firstKey());
        assertEquals(9, cfg.lastKey());
        assertEquals(3, cfg.hotbarIndex(0));
        assertEquals(8, cfg.hotbarIndex(5));
        assertEquals(0, cfg.barIndexFromHotbar(3));
        assertEquals(5, cfg.barIndexFromHotbar(8));
        assertEquals(-1, cfg.barIndexFromHotbar(2));
        assertEquals(3, cfg.weaponSlotCount());
    }

    @Test
    void parsesSwapTriggers() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ability-bar.dual-hotbar", true);
        yaml.set("ability-bar.swap-triggers", List.of("PICK_BLOCK", "SWAP_HANDS", "MIDDLE_MOUSE"));
        AbilityBarConfig cfg = new AbilityBarConfig(yaml);
        assertTrue(cfg.dualHotbar());
        assertTrue(cfg.swapTrigger(AbilityBarConfig.SwapTrigger.PICK_BLOCK));
        assertTrue(cfg.swapTrigger(AbilityBarConfig.SwapTrigger.SWAP_HANDS));
    }
}
