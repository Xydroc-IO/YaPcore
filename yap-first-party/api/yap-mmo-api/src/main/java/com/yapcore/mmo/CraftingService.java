package com.yapcore.mmo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CraftingService {

    Collection<CraftingRecipe> recipes();

    List<CraftingRecipe> recipesForSkill(SkillId skill);

    Optional<CraftingRecipe> recipe(String id);

    CompletableFuture<Boolean> isUnlocked(UUID playerId, CraftingRecipe recipe);
}
