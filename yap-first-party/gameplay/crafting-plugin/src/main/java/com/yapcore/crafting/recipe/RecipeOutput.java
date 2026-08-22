package com.yapcore.crafting.recipe;

import org.bukkit.Material;

public record RecipeOutput(
        Material material,
        int amount,
        String displayName,
        String gearTier) {

    public RecipeOutput {
        if (amount < 1) {
            amount = 1;
        }
    }
}
