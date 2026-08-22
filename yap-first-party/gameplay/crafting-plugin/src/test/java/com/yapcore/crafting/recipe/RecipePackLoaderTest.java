package com.yapcore.crafting.recipe;

import com.yapcore.mmo.RecipeKind;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePackLoaderTest {

    @TempDir
    Path recipesDir;

    @Test
    void loadsSmithingSmeltAndForgeRecipes() throws Exception {
        Path file = recipesDir.resolve("smithing.yml");
        Files.writeString(file, """
                recipes:
                  smelt_iron:
                    type: SMITHING
                    station: FURNACE
                    skill: smithing
                    level: 15
                    inputs:
                      - material: IRON_ORE
                        amount: 1
                    output:
                      material: IRON_INGOT
                      amount: 1
                    xp: 12.5
                  iron_dagger:
                    type: SMITHING
                    station: ANVIL
                    skill: smithing
                    level: 15
                    inputs:
                      - material: IRON_INGOT
                        amount: 1
                    output:
                      material: IRON_SWORD
                      amount: 1
                      gear-tier: iron
                      display-name: Iron Dagger
                    xp: 37.5
                """);

        RecipePackLoader loader = new RecipePackLoader(recipesDir);
        loader.reload();

        assertEquals(2, loader.recipes().size());
        RecipeDefinition smelt = loader.get("smelt_iron");
        assertNotNull(smelt);
        assertEquals(RecipeKind.SMITHING, smelt.kind());
        assertEquals(StationType.FURNACE, smelt.station());
        assertEquals(SkillId.of("smithing"), smelt.skill());
        assertEquals(15, smelt.level());
        assertEquals(12.5, smelt.xp(), 0.001);
        assertEquals(Material.IRON_ORE, smelt.inputs().getFirst().material());

        RecipeDefinition dagger = loader.get("iron_dagger");
        assertNotNull(dagger);
        assertEquals(StationType.ANVIL, dagger.station());
        assertEquals("iron", dagger.output().gearTier());
    }

    @Test
    void loadsCookingBurnFields() throws Exception {
        Path file = recipesDir.resolve("cooking.yml");
        Files.writeString(file, """
                recipes:
                  cook_fish:
                    type: COOKING
                    skill: cooking
                    level: 1
                    inputs:
                      - material: COD
                        amount: 1
                    output:
                      material: COOKED_COD
                      amount: 1
                    xp: 30
                    burn-level: 34
                    burn-chance: 0.5
                    burn-output: CHARCOAL
                """);

        RecipePackLoader loader = new RecipePackLoader(recipesDir);
        loader.reload();
        RecipeDefinition recipe = loader.get("cook_fish");
        assertNotNull(recipe);
        assertTrue(recipe.hasBurnMechanic());
        assertEquals(34, recipe.burnLevel());
        assertEquals(0.5, recipe.burnChance(), 0.001);
        assertEquals(Material.CHARCOAL, recipe.burnOutput());
    }

    @Test
    void parseHelperReadsInlineSection() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                id: test_recipe
                type: CRAFTING
                skill: crafting
                level: 5
                inputs:
                  - material: STICK
                    amount: 2
                output:
                  material: BOW
                  amount: 1
                xp: 10
                """));
        RecipeDefinition def = RecipePackLoader.parse("fallback", yaml);
        assertNotNull(def);
        assertEquals("test_recipe", def.id());
        assertEquals(RecipeKind.CRAFTING, def.kind());
        assertEquals(StationType.CRAFTING_TABLE, def.station());
    }
}
