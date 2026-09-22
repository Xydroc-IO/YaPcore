package com.yapcore.items.item;

import com.yapcore.items.ItemsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registers Bukkit recipes from item defs. Custom YaP ingredients use
 * {@link RecipeChoice.MaterialChoice} on their base material so crafting tables
 * and crafters can match; {@link CustomRecipeListener} enforces exact item ids.
 */
public final class RecipeRegistrar {

    private final ItemsPlugin plugin;
    private final ItemFactory factory;
    private final ItemRegistry registry;
    private final Set<NamespacedKey> registered = new HashSet<>();
    private final Map<NamespacedKey, CustomRecipeSpec> specs = new LinkedHashMap<>();

    public RecipeRegistrar(ItemsPlugin plugin, ItemFactory factory, ItemRegistry registry) {
        this.plugin = plugin;
        this.factory = factory;
        this.registry = registry;
    }

    public void unregisterAll() {
        for (NamespacedKey key : List.copyOf(registered)) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
        specs.clear();
    }

    public void registerAll() {
        unregisterAll();
        for (ItemDefinition def : registry.all().values()) {
            if (def.recipe() == null) {
                continue;
            }
            ItemStack result = factory.build(def, 1);
            NamespacedKey key = new NamespacedKey(plugin, "item_" + def.id());
            ItemDefinition.RecipeDef recipe = def.recipe();
            String type = recipe.type() == null ? "shaped" : recipe.type().toLowerCase(Locale.ROOT);
            if ("shapeless".equals(type)) {
                registerShapeless(key, def.id(), result, recipe);
            } else {
                registerShaped(key, def.id(), result, recipe);
            }
        }
        plugin.getLogger().info("Registered " + registered.size() + " custom item recipes");
    }

    public Optional<CustomRecipeSpec> spec(NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specs.get(key));
    }

    public Set<NamespacedKey> keys() {
        return Collections.unmodifiableSet(registered);
    }

    public Map<NamespacedKey, CustomRecipeSpec> specs() {
        return Collections.unmodifiableMap(specs);
    }

    private void registerShapeless(NamespacedKey key, String resultId, ItemStack result,
                                   ItemDefinition.RecipeDef recipe) {
        List<String> refs = recipe.shapeless();
        if (refs == null || refs.isEmpty()) {
            return;
        }
        ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
        for (String ref : refs) {
            RecipeChoice choice = choiceOf(ref);
            if (choice == null) {
                plugin.getLogger().warning("Recipe ingredient missing: " + ref + " for " + key.getKey());
                return;
            }
            shapeless.addIngredient(choice);
        }
        if (Bukkit.addRecipe(shapeless)) {
            registered.add(key);
            specs.put(key, new CustomRecipeSpec(key, resultId, false, null, null, List.copyOf(refs)));
        }
    }

    private void registerShaped(NamespacedKey key, String resultId, ItemStack result,
                                ItemDefinition.RecipeDef recipe) {
        if (recipe.shape() == null || recipe.shape().isEmpty()) {
            return;
        }
        ShapedRecipe shaped = new ShapedRecipe(key, result);
        List<String> shape = new ArrayList<>(recipe.shape());
        shaped.shape(shape.toArray(String[]::new));
        Map<Character, String> mapped = new HashMap<>();
        for (Map.Entry<Character, String> e : recipe.ingredients().entrySet()) {
            RecipeChoice choice = choiceOf(e.getValue());
            if (choice == null) {
                plugin.getLogger().warning("Recipe ingredient missing: " + e.getValue() + " for " + key.getKey());
                return;
            }
            shaped.setIngredient(e.getKey(), choice);
            mapped.put(e.getKey(), e.getValue());
        }
        if (Bukkit.addRecipe(shaped)) {
            registered.add(key);
            specs.put(key, new CustomRecipeSpec(
                    key, resultId, true, List.copyOf(shape), Map.copyOf(mapped), null));
        }
    }

    private RecipeChoice choiceOf(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(ref);
        if (mat != null && mat != Material.AIR && mat.isItem()) {
            return new RecipeChoice.MaterialChoice(mat);
        }
        // Custom YaP id → match on base material only; id check is in CustomRecipeListener.
        ItemDefinition def = registry.get(ref).orElse(null);
        if (def == null) {
            return null;
        }
        Material base = def.base();
        if (base == null || base == Material.AIR || !base.isItem()) {
            return null;
        }
        return new RecipeChoice.MaterialChoice(base);
    }
}
