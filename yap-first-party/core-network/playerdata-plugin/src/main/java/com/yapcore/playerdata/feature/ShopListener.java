package com.yapcore.playerdata.feature;

import com.yapcore.playerdata.cmd.ShopCommands;
import com.yapcore.playerdata.db.ShopRepository;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ShopListener implements Listener {
    private final ShopCommands shops;

    public ShopListener(ShopCommands shops) {
        this.shops = shops;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Chest chest)) {
            return;
        }
        Player player = event.getPlayer();
        try {
            var opt = shops.shops().findAt(
                    shops.config().serverId(),
                    block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            if (opt.isEmpty()) {
                return;
            }
            event.setCancelled(true);
            shops.tryBuy(player, opt.get(), chest);
        } catch (Exception e) {
            player.sendMessage("§cShop error: " + e.getMessage());
        }
    }
}
