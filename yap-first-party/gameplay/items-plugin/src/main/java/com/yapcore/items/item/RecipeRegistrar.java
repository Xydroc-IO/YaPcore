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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registers Bukkit recipes from item definitions. */
public final class RecipeRegistrar {

    private final ItemsPlugin plugin;
    private final ItemFactory factory;
    private final ItemRegistry registry;
    private final Set<NamespacedKey> registered = new HashSet<>();

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
            String type = recipe.type() == null ? "shaped" : recipe.type().toLowerCase();
            if ("shapeless".equals(type)) {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                List<Material> mats = recipe.shapeless();
                if (mats == null || mats.isEmpty()) {
                    continue;
                }
                for (Material mat : mats) {
                    shapeless.addIngredient(mat);
                }
                if (Bukkit.addRecipe(shapeless)) {
                    registered.add(key);
                }
            } else {
                if (recipe.shape() == null || recipe.shape().isEmpty()) {
                    continue;
                }
                ShapedRecipe shaped = new ShapedRecipe(key, result);
                List<String> shape = new ArrayList<>(recipe.shape());
                shaped.shape(shape.toArray(String[]::new));
                for (Map.Entry<Character, Material> e : recipe.ingredients().entrySet()) {
                    shaped.setIngredient(e.getKey(), e.getValue());
                }
                if (Bukkit.addRecipe(shaped)) {
                    registered.add(key);
                }
            }
        }
        plugin.getLogger().info("Registered " + registered.size() + " custom item recipes");
    }
}
