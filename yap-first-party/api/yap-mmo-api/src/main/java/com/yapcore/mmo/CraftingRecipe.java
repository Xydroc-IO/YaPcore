package com.yapcore.mmo;

/** Public recipe summary for quests and other plugins. */
public record CraftingRecipe(
        String id,
        RecipeKind kind,
        SkillId skill,
        int level,
        double xp,
        String displayName) {
}
