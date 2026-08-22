package com.yapcore.crafting.recipe;

import com.yapcore.mmo.RecipeKind;
import org.bukkit.Material;

import java.util.List;

public enum StationType {
    FURNACE,
    ANVIL,
    CRAFTING_TABLE;

    public static StationType defaultFor(RecipeKind kind, List<RecipeInput> inputs) {
        return switch (kind) {
            case COOKING -> FURNACE;
            case CRAFTING -> CRAFTING_TABLE;
            case SMITHING -> inputs.stream().anyMatch(i -> isOreOrRaw(i.material()))
                    ? FURNACE
                    : ANVIL;
        };
    }

    private static boolean isOreOrRaw(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.startsWith("RAW_") || name.equals("ANCIENT_DEBRIS");
    }
}
