package com.yapcore.crafting.recipe;

import com.yapcore.mmo.CraftingRecipe;
import com.yapcore.mmo.RecipeKind;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;

import java.util.List;

public record RecipeDefinition(
        String id,
        RecipeKind kind,
        SkillId skill,
        StationType station,
        int level,
        List<RecipeInput> inputs,
        RecipeOutput output,
        double xp,
        int burnLevel,
        double burnChance,
        Material burnOutput,
        String displayName) {

    public RecipeDefinition {
        inputs = List.copyOf(inputs);
    }

    public CraftingRecipe toPublic() {
        return new CraftingRecipe(id, kind, skill, level, xp, displayName);
    }

    public boolean hasBurnMechanic() {
        return burnLevel > 0 && burnChance > 0;
    }
}
