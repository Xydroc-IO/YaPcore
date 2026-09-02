package com.yapcore.mechanics.listener;

import com.yapcore.mechanics.service.MechanicsServiceImpl;
import com.yapcore.sched.StaffBypass;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockBreakMechanicsListener implements Listener {

    private final MechanicsServiceImpl mechanics;

    public BlockBreakMechanicsListener(MechanicsServiceImpl mechanics) {
        this.mechanics = mechanics;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!mechanics.config().enabled()) {
            return;
        }
        if (StaffBypass.mmo(event.getPlayer())) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        var denied = mechanics.breakDeniedReason(event.getPlayer(), event.getBlock().getType(), tool);
        if (denied.isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c" + denied.get());
            return;
        }
        if (mechanics.config().staminaEnabled()) {
            if (!mechanics.consumeStamina(event.getPlayer(), mechanics.config().breakCost())) {
                event.setCancelled(true);
                if (mechanics.config().exhaustedMessage()) {
                    event.getPlayer().sendMessage("§cYou are too exhausted to break blocks.");
                }
            }
        }
    }
}
