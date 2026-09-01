package com.yapcore.combat.service;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies custom combat damage to non-player entities without re-entering the
 * YaPCombat hit pipeline (vanilla damage is otherwise cancelled).
 */
public final class CombatEntityDamager {

    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    public boolean isBypassing(Entity entity) {
        return entity != null && bypass.contains(entity.getUniqueId());
    }

    /**
     * @param combatPoints RS-style hit points from the formula
     * @param healthPerPoint vanilla HP removed per combat point (default 2 = one heart)
     */
    public void applyToMob(LivingEntity victim, Entity source, int combatPoints, double healthPerPoint) {
        if (victim == null || !victim.isValid() || victim.isDead() || victim instanceof Player) {
            return;
        }
        double amount = Math.max(0.5, combatPoints * Math.max(0.25, healthPerPoint));
        UUID id = victim.getUniqueId();
        bypass.add(id);
        try {
            if (source != null) {
                victim.damage(amount, source);
            } else {
                victim.damage(amount);
            }
            if (!victim.isDead() && victim.getHealth() > 0) {
                try {
                    victim.playHurtAnimation(0f);
                } catch (Throwable ignored) {
                    // older API
                }
            }
        } finally {
            bypass.remove(id);
        }
    }
}
