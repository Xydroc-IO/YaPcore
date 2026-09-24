package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Keys;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.persist.PlotStore;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Bone meal advances YaP420 plant stages (farmland, air plot, or plant ItemDisplay). */
public final class BoneMealListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final Yap420Keys keys;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;

    public BoneMealListener(
            Yap420Plugin plugin,
            Yap420Config config,
            Yap420Keys keys,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store
    ) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoneMealBlock(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack meal = boneMealIn(event.getHand(), player);
        if (meal == null) {
            return;
        }
        PlotState plot = null;
        Entity looked = player.getTargetEntity(6);
        if (looked != null) {
            plot = findPlotFromEntity(looked).orElse(null);
        }
        Block clicked = event.getClickedBlock();
        if (plot == null && clicked != null) {
            plot = findPlotAtBlock(clicked).orElse(null);
        }
        if (plot == null) {
            return;
        }
        event.setCancelled(true);
        applyBoneMeal(player, meal, plot);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoneMealEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack meal = boneMealIn(event.getHand(), player);
        if (meal == null) {
            return;
        }
        PlotState plot = findPlotFromEntity(event.getRightClicked()).orElse(null);
        if (plot == null) {
            return;
        }
        event.setCancelled(true);
        applyBoneMeal(player, meal, plot);
    }

    private static ItemStack boneMealIn(EquipmentSlot hand, Player player) {
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        return stack.getType() == Material.BONE_MEAL ? stack : null;
    }

    private Optional<PlotState> findPlotAtBlock(Block block) {
        Optional<PlotState> at = plots.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (at.isPresent()) {
            return at;
        }
        Block above = block.getRelative(0, 1, 0);
        Optional<PlotState> up = plots.get(above.getWorld().getName(), above.getX(), above.getY(), above.getZ());
        if (up.isPresent()) {
            return up;
        }
        Block below = block.getRelative(0, -1, 0);
        return plots.get(below.getWorld().getName(), below.getX(), below.getY(), below.getZ());
    }

    private Optional<PlotState> findPlotFromEntity(Entity entity) {
        if (!(entity instanceof ItemDisplay display)) {
            return Optional.empty();
        }
        String key = display.getPersistentDataContainer().get(keys.plotId(), PersistentDataType.STRING);
        if (key != null && !key.isBlank()) {
            String[] parts = key.split("\\|");
            if (parts.length == 4) {
                try {
                    Optional<PlotState> byKey = plots.get(
                            parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    if (byKey.isPresent()) {
                        return byKey;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        Location loc = entity.getLocation();
        Optional<PlotState> at = plots.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (at.isPresent()) {
            return at;
        }
        return plots.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
    }

    private void applyBoneMeal(Player player, ItemStack meal, PlotState plot) {
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        if (plot.stage() >= config.maxStageIndex()) {
            player.sendMessage(LEGACY.deserialize("&7Already mature."));
            return;
        }
        YapSched.region(plugin, player.getWorld(), plot.x(), plot.z(), () -> {
            PlotState current = plots.get(plot.world(), plot.x(), plot.y(), plot.z()).orElse(null);
            if (current == null || current.stage() >= config.maxStageIndex()) {
                if (current != null) {
                    player.sendMessage(LEGACY.deserialize("&7Already mature."));
                }
                return;
            }
            int bump = 1 + (player.isSneaking() ? 0 : ThreadLocalRandom.current().nextInt(0, 2));
            int nextStage = Math.min(config.maxStageIndex(), current.stage() + bump);
            if (nextStage <= current.stage()) {
                nextStage = current.stage() + 1;
            }
            nextStage = Math.min(config.maxStageIndex(), nextStage);
            PlotState grown = current.withStage(nextStage, current.entityUuid());
            displays.safeUpdate(grown, plots);
            store.saveAsync();
            if (player.getGameMode() != GameMode.CREATIVE) {
                meal.setAmount(meal.getAmount() - 1);
            }
            Location at = new Location(player.getWorld(), grown.x() + 0.5, grown.y() + 0.6, grown.z() + 0.5);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.25, 0.35, 0.25, 0.01);
            player.sendMessage(LEGACY.deserialize(
                    "&aGrew to stage &f" + (grown.stage() + 1) + "&a/&f" + config.stages() + "&a."));
        });
    }
}
