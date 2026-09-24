package com.yapcore.claims;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

final class ClaimListenerCombat implements Listener {

    private final ClaimService claims;

    ClaimListenerCombat(ClaimService claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var claim = claims.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return;
        }
        if (!claims.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ClaimExplosionRules.Kind boom = ClaimExplosionRules.kindOf(event.getDamager());
        if (boom != ClaimExplosionRules.Kind.NONE
                && event.getEntity() instanceof ArmorStand stand
                && !explosionAllowed(boom, stand.getLocation())) {
            event.setCancelled(true);
            return;
        }
        Player attacker = ClaimListenerEntities.resolvePlayerDamager(event.getDamager());
        if (attacker != null
                && claims.getAt(attacker.getLocation()).isPresent()
                && !claims.isDamageAllowed(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var claim = claims.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return;
        }
        if (!claims.isDamageAllowed(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (attacker == null) {
            return;
        }
        if (!attacker.hasPermission("yapdata.claims.admin")
                && !claims.isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP disabled in this claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (ClaimListenerEntities.resolvePlayerDamager(event.getDamager()) != null) {
            return;
        }
        if (!claims.isMobDamageAllowed(victim)) {
            event.setCancelled(true);
        }
    }

    private boolean explosionAllowed(ClaimExplosionRules.Kind kind, org.bukkit.Location location) {
        return switch (kind) {
            case TNT -> claims.isTntAllowed(location);
            case CREEPER -> claims.isCreeperExplosionAllowed(location);
            case NONE -> true;
        };
    }
}
