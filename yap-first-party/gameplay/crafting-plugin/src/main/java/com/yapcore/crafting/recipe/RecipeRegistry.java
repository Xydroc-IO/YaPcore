package com.yapcore.crafting.recipe;

import com.yapcore.mmo.RecipeKind;
import com.yapcore.mmo.SkillId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecipeRegistry {

    private final Map<String, RecipeDefinition> byId;
    private final List<RecipeDefinition> furnaceRecipes;
    private final List<RecipeDefinition> anvilRecipes;
    private final List<RecipeDefinition> craftingTableRecipes;

    public RecipeRegistry(Map<String, RecipeDefinition> byId) {
        this.byId = Map.copyOf(byId);
        List<RecipeDefinition> furnace = new ArrayList<>();
        List<RecipeDefinition> anvil = new ArrayList<>();
        List<RecipeDefinition> table = new ArrayList<>();
        for (RecipeDefinition def : byId.values()) {
            switch (def.station()) {
                case FURNACE -> furnace.add(def);
                case ANVIL -> anvil.add(def);
                case CRAFTING_TABLE -> table.add(def);
            }
        }
        this.furnaceRecipes = List.copyOf(furnace);
        this.anvilRecipes = List.copyOf(anvil);
        this.craftingTableRecipes = List.copyOf(table);
    }

    public Collection<RecipeDefinition> all() {
        return byId.values();
    }

    public Optional<RecipeDefinition> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<RecipeDefinition> forStation(StationType station) {
        return switch (station) {
            case FURNACE -> furnaceRecipes;
            case ANVIL -> anvilRecipes;
            case CRAFTING_TABLE -> craftingTableRecipes;
        };
    }

    public List<RecipeDefinition> forSkill(SkillId skill) {
        return byId.values().stream()
                .filter(r -> r.skill().equals(skill))
                .toList();
    }

    public List<RecipeDefinition> forKind(RecipeKind kind) {
        return byId.values().stream()
                .filter(r -> r.kind() == kind)
                .toList();
    }
}
