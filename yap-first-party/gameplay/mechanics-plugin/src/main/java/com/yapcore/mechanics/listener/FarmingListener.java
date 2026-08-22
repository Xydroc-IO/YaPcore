package com.yapcore.mechanics.listener;

import com.yapcore.mechanics.farming.FarmingLoader;
import com.yapcore.mechanics.service.MechanicsServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class FarmingListener implements Listener {

    private final JavaPlugin plugin;
    private final MechanicsServiceImpl mechanics;
    private final FarmingLoader farming;

    public FarmingListener(JavaPlugin plugin, MechanicsServiceImpl mechanics, FarmingLoader farming) {
        this.plugin = plugin;
        this.mechanics = mechanics;
        this.farming = farming;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!mechanics.config().farmingEnabled()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        FarmingLoader.CropDef crop = farming.byCrop(block.getType());
        if (crop != null) {
            harvestCrop(event, player, block, crop);
            return;
        }
        if (block.getType() == Material.FARMLAND) {
            plantSeed(event, player, block, event.getItem());
        }
    }

    private void harvestCrop(PlayerInteractEvent event, Player player, Block block, FarmingLoader.CropDef crop) {
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() < crop.matureAge()) {
            player.sendMessage("§7This crop is not ready yet.");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        YapSched.region(plugin, block.getLocation(), () -> {
            for (FarmingLoader.Drop drop : crop.drops()) {
                int amount = ThreadLocalRandom.current().nextInt(drop.min(), drop.max() + 1);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(drop.material(), amount));
            }
            if (crop.replant()) {
                ageable.setAge(0);
                block.setBlockData(ageable);
            } else {
                block.setType(Material.AIR);
            }
        });
    }

    private void plantSeed(PlayerInteractEvent event, Player player, Block farmland, ItemStack hand) {
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        FarmingLoader.CropDef crop = farming.bySeed(hand.getType());
        if (crop == null) {
            return;
        }
        Block above = farmland.getRelative(0, 1, 0);
        if (!above.getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        YapSched.region(plugin, above.getLocation(), () -> {
            above.setType(crop.cropBlock());
            if (hand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }
        });
    }
}
