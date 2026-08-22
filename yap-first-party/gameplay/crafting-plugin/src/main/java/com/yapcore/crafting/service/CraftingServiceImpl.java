package com.yapcore.crafting.service;

import com.yapcore.crafting.recipe.RecipeDefinition;
import com.yapcore.crafting.recipe.RecipeRegistry;
import com.yapcore.mmo.CraftingRecipe;
import com.yapcore.mmo.CraftingService;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class CraftingServiceImpl implements CraftingService {

    private final RecipeRegistry registry;

    public CraftingServiceImpl(RecipeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Collection<CraftingRecipe> recipes() {
        return registry.all().stream().map(RecipeDefinition::toPublic).collect(Collectors.toList());
    }

    @Override
    public List<CraftingRecipe> recipesForSkill(SkillId skill) {
        return registry.forSkill(skill).stream().map(RecipeDefinition::toPublic).toList();
    }

    @Override
    public Optional<CraftingRecipe> recipe(String id) {
        return registry.get(id).map(RecipeDefinition::toPublic);
    }

    @Override
    public CompletableFuture<Boolean> isUnlocked(UUID playerId, CraftingRecipe recipe) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return CompletableFuture.completedFuture(true);
        }
        return skills.get(playerId, recipe.skill()).thenApply(progress -> progress.level() >= recipe.level());
    }
}
