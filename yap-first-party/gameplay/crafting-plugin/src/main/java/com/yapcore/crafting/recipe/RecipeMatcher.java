package com.yapcore.crafting.recipe;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipeMatcher {

    private RecipeMatcher() {
    }

    public static Optional<RecipeDefinition> matchFurnaceInput(
            List<RecipeDefinition> candidates, ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return Optional.empty();
        }
        for (RecipeDefinition recipe : candidates) {
            if (recipe.inputs().size() != 1) {
                continue;
            }
            RecipeInput required = recipe.inputs().getFirst();
            if (input.getType() == required.material() && input.getAmount() >= required.amount()) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static Optional<RecipeDefinition> matchGrid(
            List<RecipeDefinition> candidates, ItemStack[] grid) {
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (stack != null && !stack.getType().isAir()) {
                present.add(stack);
            }
        }
        if (present.isEmpty()) {
            return Optional.empty();
        }
        for (RecipeDefinition recipe : candidates) {
            if (matchesInputs(recipe, present)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesInputs(RecipeDefinition recipe, List<ItemStack> present) {
        List<RecipeInput> required = recipe.inputs();
        if (present.size() != required.size()) {
            return false;
        }
        List<RecipeInput> remaining = new ArrayList<>(required);
        for (ItemStack stack : present) {
            boolean matched = false;
            for (int i = 0; i < remaining.size(); i++) {
                RecipeInput req = remaining.get(i);
                if (stack.getType() == req.material() && stack.getAmount() >= req.amount()) {
                    remaining.remove(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return remaining.isEmpty();
    }
}
