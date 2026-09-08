package com.yapcore.items;

import com.yapcore.items.ability.AbilityDefinition;
import com.yapcore.items.ability.AbilityType;
import com.yapcore.items.furniture.FurnitureService;
import com.yapcore.items.item.ItemWriter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsUnitTest {

    @Test
    void parsesCooldownUnits() {
        assertEquals(8000L, AbilityDefinition.parseCooldown("8s"));
        assertEquals(500L, AbilityDefinition.parseCooldown("500ms"));
        assertEquals(50L, AbilityDefinition.parseCooldown("1t"));
        assertEquals(0L, AbilityDefinition.parseCooldown(""));
    }

    @Test
    void abilityTypesCoverPlan() {
        for (String name : new String[]{
                "message", "effect", "heal", "feed", "launch", "dash", "lightning",
                "lightning_dash", "smite_target", "explode", "projectile",
                "command_player", "command_console", "sound", "particle",
                "cleanse", "absorb", "fireball", "pull", "push", "blink", "ground_slam", "repair"
        }) {
            AbilityType.parse(name);
        }
        assertEquals(AbilityType.LIGHTNING_DASH, AbilityType.parse("lightning-dash"));
        assertEquals(AbilityDefinition.Trigger.TOGETHER, AbilityDefinition.Trigger.parse("none"));
        assertEquals(AbilityDefinition.Trigger.SNEAK_RIGHT_CLICK, AbilityDefinition.Trigger.parse("sneak_rmb"));
    }

    @Test
    void cmdRangeValidation() {
        ItemsConfig cfg = new ItemsConfig(new org.bukkit.configuration.file.YamlConfiguration() {{
            set("cmd-min", 12000);
            set("cmd-max", 12999);
        }});
        assertTrue(cfg.inCmdRange(12001));
        assertTrue(cfg.inCmdRange(12999));
        assertTrue(!cfg.inCmdRange(11999));
    }

    @Test
    void furnitureRecordKeyStable() {
        var rec = new FurnitureService.FurnitureRecord("world", 1, 2, 3, "stone_pedestal", 90f, null);
        assertEquals("world;1;2;3", rec.key());
        assertEquals(FurnitureService.FurnitureRecord.keyOf("world", 1, 2, 3), rec.key());
    }

    @Test
    void templateCmdsMatchPackSlots() {
        assertEquals(12010, ItemWriter.templateCmd("sword", 0));
        assertEquals(12011, ItemWriter.templateCmd("tool", 0));
        assertEquals(12012, ItemWriter.templateCmd("gem", 0));
        assertEquals(12013, ItemWriter.templateCmd("prop", 0));
        assertEquals(org.bukkit.Material.NETHERITE_AXE, ItemWriter.templateBase("axe"));
        assertEquals(org.bukkit.Material.NETHERITE_SPEAR, ItemWriter.templateBase("spear"));
        assertEquals(org.bukkit.Material.MACE, ItemWriter.templateBase("mace"));
        assertEquals(org.bukkit.Material.BOW, ItemWriter.templateBase("bow"));
        assertEquals(0, ItemWriter.templateCmd("axe", 0));
    }

    @Test
    void normalizeCooldownAddsSecondsSuffix() {
        assertEquals("0s", ItemWriter.normalizeCooldown("0"));
        assertEquals("8s", ItemWriter.normalizeCooldown("8s"));
        assertEquals("2.5s", ItemWriter.normalizeCooldown("2.5"));
    }
}
