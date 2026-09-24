package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

final class IslandListenerCombat implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerCombat(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (!access.pvpAllowed(victim.getLocation()) || !access.pvpAllowed(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    private static Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
