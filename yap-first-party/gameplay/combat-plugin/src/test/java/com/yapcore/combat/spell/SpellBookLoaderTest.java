package com.yapcore.combat.spell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellBookLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSpellDefinitions() throws Exception {
        Files.writeString(tempDir.resolve("spells.yml"), """
                spells:
                  wind_strike:
                    name: Wind Strike
                    min-magic-level: 1
                    prayer-cost: 1
                    max-hit: 4
                    cast-xp: 12
                    runes:
                      FEATHER: 1
                  crumble:
                    name: Crumble Undead
                    min-magic-level: 25
                    target-filter: undead
                """);
        SpellBookLoader loader = new SpellBookLoader();
        loader.load(tempDir.resolve("spells.yml"));
        SpellDefinition spell = loader.get("wind_strike");
        assertNotNull(spell);
        assertEquals(1, spell.minMagicLevel());
        assertEquals(4, spell.baseMaxHit());
        assertEquals(1, spell.runes().size());
        SpellDefinition crumble = loader.get("crumble");
        assertNotNull(crumble);
        assertTrue(crumble.hasTargetFilter());
    }
}
