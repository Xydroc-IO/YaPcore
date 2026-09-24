package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class IslandListenerInteract implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerInteract(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        Material type = block.getType();
        boolean container = type.name().contains("CHEST")
                || type.name().contains("SHULKER")
                || type == Material.BARREL
                || type == Material.HOPPER
                || type == Material.FURNACE
                || type == Material.BLAST_FURNACE
                || type == Material.SMOKER
                || type == Material.BREWING_STAND
                || type.name().contains("DOOR")
                || type.name().contains("GATE")
                || type.name().contains("BUTTON")
                || type.name().contains("LEVER")
                || type.name().contains("PRESSURE");
        if (!container) {
            return;
        }
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!access.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot use that here.", NamedTextColor.RED));
        }
    }
}
