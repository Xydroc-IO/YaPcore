package com.yapcore.stacker;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Stack tool interactions + kill-aura held item pulses. */
public final class ToolListener implements Listener {

    private final StackService stacks;
    private final StackerItems tools;

    public ToolListener(StackService stacks, StackerItems tools) {
        this.stacks = stacks;
        this.tools = tools;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity living)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!tools.isTool(hand) || !player.hasPermission("yapstacker.tool")) {
            return;
        }
        event.setCancelled(true);
        int absorbed = stacks.forceMergeNearby(living);
        player.sendMessage("Stack=" + stacks.getStack(living) + " absorbed=" + absorbed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitInfo(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!tools.isTool(hand) || !player.hasPermission("yapstacker.tool")) {
            return;
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Stack " + living.getType() + " x" + stacks.getStack(living)));
    }

    /**
     * Kill up to {@code kills-per-pulse} stack units among nearby mobs.
     * Each unit is a full lethal hit so EntityDeathListener applies loot/DECREMENT.
     */
    public void pulseAura(Player player) {
        if (!stacks.config().killAuraEnabled()) {
            return;
        }
        if (!player.hasPermission("yapstacker.aura")) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!tools.isAura(hand)) {
            return;
        }
        double r = stacks.config().killAuraRadius();
        int budget = stacks.config().killAuraKillsPerPulse();
        for (var e : player.getNearbyEntities(r, r, r)) {
            if (budget <= 0) {
                break;
            }
            if (!(e instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            if (!living.isValid() || living.isDead()) {
                continue;
            }
            living.damage(Math.max(living.getHealth(), 1.0) + 1.0, player);
            stacks.metrics().auraKill();
            budget--;
        }
    }
}
