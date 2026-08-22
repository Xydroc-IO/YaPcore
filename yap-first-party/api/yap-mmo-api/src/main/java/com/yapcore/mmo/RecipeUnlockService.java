package com.yapcore.mmo;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional recipe unlock tracking (YaPCrafting or YaPMmoContent). */
public interface RecipeUnlockService {

    CompletableFuture<Boolean> isUnlocked(UUID playerId, String recipeId);

    CompletableFuture<Void> unlock(UUID playerId, String recipeId);
}
