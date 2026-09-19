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
    void clayBallFallsBackToVanillaToolIcon() throws Exception {
        Files.writeString(tempDir.resolve("mining.yml"), """
                id: mining
                icon: CLAY_BALL
                break:
                  STONE:
                    xp: 5
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        assertEquals(Material.IRON_PICKAXE, loader.get(SkillId.of("mining")).icon());
    }

    @Test
    void loadsMarathonTravel() throws Exception {
        Files.writeString(tempDir.resolve("marathon.yml"), """
                id: marathon
                travel:
                  xp-per-block: 1.0
                  max-blocks-per-second: 12
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        SkillDefinition marathon = loader.get(SkillId.of("marathon"));
        assertNotNull(marathon);
        assertEquals(Material.LEATHER_BOOTS, marathon.icon());
        assertEquals(1.0, marathon.travel().xpPerBlock(), 0.01);
        assertEquals(12.0, marathon.travel().maxBlocksPerSecond(), 0.01);
    }

    @Test
    void loadsBuilderPlace() throws Exception {
        Files.writeString(tempDir.resolve("builder.yml"), """
                id: builder
                place:
                  xp: 2
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        SkillDefinition builder = loader.get(SkillId.of("builder"));
        assertNotNull(builder);
        assertEquals(Material.BRICKS, builder.icon());
        assertEquals(2.0, builder.place().xp(), 0.01);
    }

    @Test
    void loadsAlchemyAndExcavation() throws Exception {
        Files.writeString(tempDir.resolve("alchemy.yml"), """
                id: alchemy
                brew:
                  xp: 25
                """);
        Files.writeString(tempDir.resolve("excavation.yml"), """
                id: excavation
                treasure:
                  DIAMOND:
                    chance: 0.0004
                    amount: 1
                """);
        SkillPackLoader loader = new SkillPackLoader(tempDir);
        loader.reload();
        SkillDefinition alchemy = loader.get(SkillId.of("alchemy"));
        assertNotNull(alchemy);
        assertEquals(Material.BREWING_STAND, alchemy.icon());
        assertEquals(25.0, alchemy.brew().xp(), 0.01);
        SkillDefinition excavation = loader.get(SkillId.of("excavation"));
        assertNotNull(excavation);
        assertEquals(Material.IRON_SHOVEL, excavation.icon());
        assertEquals(1, excavation.treasure().size());
        assertEquals(Material.DIAMOND, excavation.treasure().get(0).item());
    }

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
