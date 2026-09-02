package com.yapcore.abilities.book;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityBookConfigTest {

    @Test
    void parsesOpenTriggersAndTome() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ability-book.enabled", true);
        yaml.set("ability-book.abilities-per-page", 15);
        yaml.set("ability-book.open-triggers", List.of("COMMAND", "TOME", "SNEAK_SWAP"));
        yaml.set("ability-book.tome.give-on-first-join", false);
        AbilityBookConfig cfg = new AbilityBookConfig(yaml);
        assertTrue(cfg.enabled());
        assertEquals(15, cfg.abilitiesPerPage());
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.COMMAND));
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.TOME));
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.SNEAK_SWAP));
        assertTrue(cfg.tomeEnabled());
        assertFalse(cfg.giveTomeOnFirstJoin());
    }

    @Test
    void defaultsOpenBookAndEnchantedTome() {
        AbilityBookConfig cfg = new AbilityBookConfig(new YamlConfiguration());
        assertTrue(cfg.enabled());
        assertTrue(cfg.showLocked());
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.COMMAND));
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.TOME));
        assertTrue(cfg.openTrigger(AbilityBookConfig.OpenTrigger.SNEAK_SWAP));
        assertEquals("ENCHANTED_BOOK", cfg.tomeMaterialName());
    }
}
