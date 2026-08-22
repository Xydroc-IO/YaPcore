package com.yapcore.crafting.listener;

import com.yapcore.crafting.recipe.RecipeDefinition;
import com.yapcore.crafting.recipe.RecipeMatcher;
import com.yapcore.crafting.recipe.RecipeRegistry;
import com.yapcore.crafting.recipe.StationType;
import com.yapcore.crafting.service.RecipeExecutor;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FurnaceStationListener implements Listener {

    private final JavaPlugin plugin;
    private final RecipeRegistry registry;
    private final RecipeExecutor executor;
    private final Map<Location, UUID> lastInteractor = new ConcurrentHashMap<>();

    public FurnaceStationListener(JavaPlugin plugin, RecipeRegistry registry, RecipeExecutor executor) {
        this.plugin = plugin;
        this.registry = registry;
        this.executor = executor;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.FURNACE
                && event.getInventory().getType() != InventoryType.BLAST_FURNACE
                && event.getInventory().getType() != InventoryType.SMOKER) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        lastInteractor.put(event.getInventory().getLocation(), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltComplete(FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        UUID uuid = lastInteractor.get(block.getLocation());
        if (uuid == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack source = event.getSource();
        Optional<RecipeDefinition> match = RecipeMatcher.matchFurnaceInput(
                registry.forStation(StationType.FURNACE), source);
        if (match.isEmpty()) {
            return;
        }
        RecipeDefinition recipe = match.get();
        Optional<String> gate = executor.levelGate(player, recipe);
        if (gate.isPresent()) {
            event.setCancelled(true);
            YapSched.entity(plugin, player, () -> player.sendMessage(gate.get()));
            return;
        }
        if (executor.shouldBurn(player, recipe)) {
            event.setResult(new ItemStack(recipe.burnOutput(), 1));
            YapSched.entity(plugin, player, () -> player.sendMessage("§cYou burned the food."));
        }
        executor.grantXp(player, recipe);
        executor.fireCrafted(player, recipe);
    }
}
