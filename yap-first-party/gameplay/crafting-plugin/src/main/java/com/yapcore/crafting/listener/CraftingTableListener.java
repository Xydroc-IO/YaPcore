package com.yapcore.crafting.listener;

import com.yapcore.crafting.recipe.RecipeDefinition;
import com.yapcore.crafting.recipe.RecipeMatcher;
import com.yapcore.crafting.recipe.RecipeRegistry;
import com.yapcore.crafting.recipe.StationType;
import com.yapcore.crafting.service.RecipeExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class CraftingTableListener implements Listener {

    private static final int[] INPUT_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int RESULT_SLOT = 0;

    private final RecipeRegistry registry;
    private final RecipeExecutor executor;

    public CraftingTableListener(RecipeRegistry registry, RecipeExecutor executor) {
        this.registry = registry;
        this.executor = executor;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.WORKBENCH
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            return;
        }
        ItemStack[] grid = crafting.getMatrix();
        Optional<RecipeDefinition> match = RecipeMatcher.matchGrid(
                registry.forStation(StationType.CRAFTING_TABLE), grid);
        if (match.isEmpty()) {
            return;
        }
        event.getInventory().setResult(executor.buildResult(match.get(), false));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.WORKBENCH
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("yapcraft.use")) {
            return;
        }
        if (event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        ItemStack[] grid = crafting.getMatrix();
        Optional<RecipeDefinition> match = RecipeMatcher.matchGrid(
                registry.forStation(StationType.CRAFTING_TABLE), grid);
        if (match.isEmpty()) {
            return;
        }
        RecipeDefinition recipe = match.get();
        Optional<String> gate = executor.levelGate(player, recipe);
        if (gate.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(gate.get());
            return;
        }
        event.setCancelled(true);
        ItemStack result = executor.buildResult(recipe, false);
        if (!hasSpace(player, result)) {
            player.sendMessage("§cYour inventory is full.");
            return;
        }
        executor.consumeInputs(crafting, INPUT_SLOTS, recipe);
        crafting.setResult(null);
        player.getInventory().addItem(result);
        executor.grantXp(player, recipe);
        executor.fireCrafted(player, recipe);
        player.sendMessage("§aCrafted §f" + recipe.displayName() + "§a.");
    }

    private static boolean hasSpace(Player player, ItemStack stack) {
        return player.getInventory().firstEmpty() >= 0
                || player.getInventory().containsAtLeast(stack, 1);
    }
}
