package com.yapcore.mmocontent.service;

import com.yapcore.mmo.RecipeUnlockService;
import com.yapcore.mmocontent.db.RecipeUnlockRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RecipeUnlockServiceImpl implements RecipeUnlockService {

    private final RecipeUnlockRepository repository;

    public RecipeUnlockServiceImpl(RecipeUnlockRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<Boolean> isUnlocked(UUID playerId, String recipeId) {
        return repository.isUnlocked(playerId, recipeId);
    }

    @Override
    public CompletableFuture<Void> unlock(UUID playerId, String recipeId) {
        return repository.unlock(playerId, recipeId);
    }
}
