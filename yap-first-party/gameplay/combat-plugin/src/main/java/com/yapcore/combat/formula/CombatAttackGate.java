package com.yapcore.combat.formula;

import com.yapcore.mmo.CombatStyle;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player attack cadence to prevent click-spam bypassing combat ticks. */
public final class CombatAttackGate {

    private final Map<UUID, Long> lastAttackTick = new ConcurrentHashMap<>();

    public boolean tryAcquire(Player player, CombatStyle style, int meleeCooldown, int rangedCooldown) {
        int cooldown = style == CombatStyle.RANGED ? rangedCooldown : meleeCooldown;
        if (cooldown <= 0) {
            return true;
        }
        long now = player.getWorld().getFullTime();
        UUID id = player.getUniqueId();
        Long last = lastAttackTick.get(id);
        if (last != null && now - last < cooldown) {
            return false;
        }
        lastAttackTick.put(id, now);
        return true;
    }

    public void clear(UUID playerId) {
        lastAttackTick.remove(playerId);
    }
}
