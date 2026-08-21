package com.yapcore.playerdata.npc;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class NpcTraderListener implements Listener {

    private final NpcTraderService traders;

    public NpcTraderListener(NpcTraderService traders) {
        this.traders = traders;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        var id = traders.readTraderId(entity);
        if (id.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        traders.openTradeGui(event.getPlayer(), id.get());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager)) {
            return;
        }
        if (traders.readTraderId(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
