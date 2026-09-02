package com.yapcore.world.listener;

import com.yapcore.world.WorldConfig;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SelectionWandListener implements Listener {

    public static final Material WAND = Material.WOODEN_AXE;

    private final WorldConfig config;
    private final SelectionServiceImpl selection;
    private final SelectionShape shapes;

    public SelectionWandListener(WorldConfig config, SelectionServiceImpl selection, SelectionShape shapes) {
        this.config = config;
        this.selection = selection;
        this.shapes = shapes;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.selectionEnabled()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapworld.selection")) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != WAND) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        var block = event.getClickedBlock();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(player.getUniqueId(), block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            if (shapes != null && shapes.mode(player.getUniqueId()) == SelectionShape.Mode.POLY) {
                shapes.addPolyPoint(player.getUniqueId(), block.getX(), block.getY(), block.getZ());
                player.sendMessage("§aPoly vertex #" + shapes.polyPoints(player.getUniqueId()).size()
                        + " §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            } else {
                player.sendMessage("§aPos1 set to §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            }
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.setPos2(player.getUniqueId(), block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            player.sendMessage("§aPos2 set to §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            event.setCancelled(true);
        }
    }
}
