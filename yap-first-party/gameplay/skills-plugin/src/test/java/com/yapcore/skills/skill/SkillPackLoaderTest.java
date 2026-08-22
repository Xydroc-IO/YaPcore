package com.yapcore.skills.skill;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillPackLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsMiningBreakMap() throws Exception {
        Files.writeString(tempDir.resolve("mining.yml"), """
                id: mining
                display: Mining
                icon: IRON_PICKAXE
                break:
                  COAL_ORE:
                    xp: 50
                    min-level: 1
                  IRON_ORE:
                    xp: 70
                    min-level: 15
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        SkillDefinition mining = loader.get(SkillId.of("mining"));
        assertNotNull(mining);
        assertEquals(50, mining.breakActions().get(Material.COAL_ORE).xp(), 0.01);
        assertEquals(15, mining.breakActions().get(Material.IRON_ORE).minLevel());
    }

    @Test
    void loadsCombatAndSmeltSections() throws Exception {
        Files.writeString(tempDir.resolve("attack.yml"), """
                id: attack
                combat-dealt:
                  xp-per-damage: 2.0
                  share: 0.5
                """);
        Files.writeString(tempDir.resolve("cooking.yml"), """
                id: cooking
                smelt:
                  COOKED_BEEF:
                    xp: 30
                    min-level: 1
                """);
        Files.writeString(tempDir.resolve("fishing.yml"), """
                id: fishing
                fish:
                  CAUGHT:
                    xp: 40
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        assertNotNull(loader.get(SkillId.of("attack")).combatDealt());
        assertEquals(0.5, loader.get(SkillId.of("attack")).combatDealt().share(), 0.001);
        assertNotNull(loader.get(SkillId.of("cooking")).smeltActions().get(Material.COOKED_BEEF));
        assertNotNull(loader.get(SkillId.of("fishing")).fishActions().get("CAUGHT"));
        assertNull(loader.get(SkillId.of("attack")).breakActions().get(Material.STONE));
    }

    @Test
    void loadsRangedMagicPrayerSections() throws Exception {
        Files.writeString(tempDir.resolve("ranged.yml"), """
                id: ranged
                ranged-dealt:
                  xp-per-damage: 2.0
                  share: 1.0
                """);
        Files.writeString(tempDir.resolve("magic.yml"), """
                id: magic
                magic-dealt:
                  xp-per-damage: 2.5
                  share: 1.0
                """);
        Files.writeString(tempDir.resolve("prayer.yml"), """
                id: prayer
                prayer-drain:
                  xp-per-point: 0.5
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        assertNotNull(loader.get(SkillId.of("ranged")).rangedDealt());
        assertNotNull(loader.get(SkillId.of("magic")).magicDealt());
        assertEquals(0.5, loader.get(SkillId.of("prayer")).prayerDrain().xpPerPoint(), 0.001);
    }
}
