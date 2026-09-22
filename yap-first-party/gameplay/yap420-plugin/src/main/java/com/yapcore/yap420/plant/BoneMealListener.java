package com.yapcore.yap420.plant;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.persist.PlotStore;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/** Bone meal advances YaP420 plant stages (farmland or plant block). */
public final class BoneMealListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final PlotRegistry plots;
    private final PlantDisplayService displays;
    private final PlotStore store;

    public BoneMealListener(
            Yap420Plugin plugin,
            Yap420Config config,
            PlotRegistry plots,
            PlantDisplayService displays,
            PlotStore store
    ) {
        this.plugin = plugin;
        this.config = config;
        this.plots = plots;
        this.displays = displays;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoneMeal(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BONE_MEAL) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        PlotState plot = plots.get(clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ())
                .or(() -> plots.get(
                        clicked.getWorld().getName(),
                        clicked.getX(),
                        clicked.getY() + 1,
                        clicked.getZ()))
                .orElse(null);
        if (plot == null) {
            return;
        }
        event.setCancelled(true);
        if (plot.stage() >= config.maxStageIndex()) {
            player.sendMessage(LEGACY.deserialize("&7Already mature."));
            return;
        }
        YapSched.region(plugin, clicked.getLocation(), () -> {
            PlotState current = plots.get(plot.world(), plot.x(), plot.y(), plot.z()).orElse(null);
            if (current == null || current.stage() >= config.maxStageIndex()) {
                return;
            }
            int nextStage = Math.min(config.maxStageIndex(), current.stage() + 1
                    + (player.isSneaking() ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 2)));
            if (nextStage <= current.stage()) {
                nextStage = current.stage() + 1;
            }
            nextStage = Math.min(config.maxStageIndex(), nextStage);
            PlotState grown = current.withStage(nextStage, current.entityUuid());
            displays.safeUpdate(grown, plots);
            store.saveAsync();
            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
            Block at = player.getWorld().getBlockAt(grown.x(), grown.y(), grown.z());
            at.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    at.getX() + 0.5,
                    at.getY() + 0.6,
                    at.getZ() + 0.5,
                    12,
                    0.25,
                    0.35,
                    0.25,
                    0.01);
            player.sendMessage(LEGACY.deserialize(
                    "&aGrew to stage &f" + (grown.stage() + 1) + "&a/&f" + config.stages() + "&a."));
        });
    }
}
