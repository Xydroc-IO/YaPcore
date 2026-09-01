package com.yapcore.world.listener;

import com.yapcore.world.edit.BrushService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-click block/air with blaze-rod brush tool. */
public final class BrushListener implements Listener {

    private final BrushService brushes;

    public BrushListener(BrushService brushes) {
        this.brushes = brushes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapworld.brush")) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != BrushService.BRUSH_TOOL) {
            return;
        }
        if (brushes.state(player.getUniqueId()) == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        event.setCancelled(true);
        var target = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : player.getLocation();
        brushes.applySphere(player, target).thenAccept(count ->
                player.sendMessage("§aBrush placed §f" + count + " §ablocks."));
    }
}
