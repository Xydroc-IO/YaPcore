package com.yapcore.items.item;

import com.yapcore.items.ItemsPlugin;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ExactChoice is too strict for tagged YaP stacks (and breaks autocrafters).
 * Recipes register on base materials; this listener requires matching item ids.
 */
public final class CustomRecipeListener implements Listener {

    private final ItemsPlugin plugin;
    private final ItemFactory factory;
    private final RecipeRegistrar recipes;

    public CustomRecipeListener(ItemsPlugin plugin, ItemFactory factory, RecipeRegistrar recipes) {
        this.plugin = plugin;
        this.factory = factory;
        this.recipes = recipes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!recipes.keys().isEmpty()) {
            player.discoverRecipes(recipes.keys());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        CustomRecipeSpec spec = specOf(recipe).orElse(null);
        if (spec == null) {
            return;
        }
        CraftingInventory inv = event.getInventory();
        if (!matches(spec, inv.getMatrix())) {
            inv.setResult(null);
            return;
        }
        factory.create(spec.resultId(), 1).ifPresentOrElse(inv::setResult, () -> inv.setResult(null));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        CustomRecipeSpec spec = specOf(event.getRecipe()).orElse(null);
        if (spec == null) {
            return;
        }
        if (!(event.getBlock().getState() instanceof Crafter crafter)) {
            event.setCancelled(true);
            return;
        }
        Inventory inv = crafter.getInventory();
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = inv.getItem(i);
        }
        if (!matches(spec, matrix)) {
            event.setCancelled(true);
            return;
        }
        ItemStack result = factory.create(spec.resultId(), 1).orElse(null);
        if (result == null) {
            event.setCancelled(true);
            return;
        }
        event.setResult(result);
    }

    private Optional<CustomRecipeSpec> specOf(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return Optional.empty();
        }
        NamespacedKey key = keyed.getKey();
        return recipes.spec(key);
    }

    boolean matches(CustomRecipeSpec spec, ItemStack[] matrix) {
        if (spec.shaped()) {
            return matchesShaped(spec, matrix);
        }
        return matchesShapeless(spec, matrix);
    }

    private boolean matchesShapeless(CustomRecipeSpec spec, ItemStack[] matrix) {
        List<String> need = new ArrayList<>(spec.shapelessIngredients());
        List<ItemStack> used = new ArrayList<>();
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            used.add(stack);
        }
        if (used.size() != need.size()) {
            return false;
        }
        boolean[] taken = new boolean[used.size()];
        for (String ref : need) {
            boolean found = false;
            for (int i = 0; i < used.size(); i++) {
                if (taken[i]) {
                    continue;
                }
                if (ingredientMatches(ref, used.get(i))) {
                    taken[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesShaped(CustomRecipeSpec spec, ItemStack[] matrix) {
        List<String> shape = spec.shape();
        Map<Character, String> map = spec.shapedIngredients();
        if (shape == null || map == null) {
            return false;
        }
        // Normalize to 3x3 like Bukkit crafting matrix.
        String[] rows = new String[3];
        for (int r = 0; r < 3; r++) {
            rows[r] = r < shape.size() ? padRow(shape.get(r)) : "   ";
        }
        for (int r = 0; r < 3; r++) {
            String row = rows[r];
            for (int c = 0; c < 3; c++) {
                char ch = row.charAt(c);
                ItemStack stack = matrix.length > r * 3 + c ? matrix[r * 3 + c] : null;
                boolean empty = stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
                if (ch == ' ' || ch == '.') {
                    if (!empty) {
                        return false;
                    }
                    continue;
                }
                String ref = map.get(ch);
                if (ref == null || empty || !ingredientMatches(ref, stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String padRow(String row) {
        if (row == null) {
            return "   ";
        }
        if (row.length() >= 3) {
            return row.substring(0, 3);
        }
        return String.format("%-3s", row);
    }

    private boolean ingredientMatches(String ref, ItemStack stack) {
        if (ref == null || stack == null) {
            return false;
        }
        Material mat = Material.matchMaterial(ref);
        if (mat != null && mat != Material.AIR) {
            // Vanilla material ingredient: accept plain stacks of that type (not a different YaP item
            // unless that YaP item's base happens to be this material — still OK for PAPER→paper).
            return stack.getType() == mat;
        }
        Optional<String> id = factory.idOf(stack);
        return id.isPresent() && id.get().equalsIgnoreCase(ref.trim().toLowerCase(Locale.ROOT));
    }
}
