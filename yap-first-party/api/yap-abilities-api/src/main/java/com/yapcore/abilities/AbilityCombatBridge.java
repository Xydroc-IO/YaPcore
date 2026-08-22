package com.yapcore.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Optional bridge registered by yap-combat for ability damage rolls.
 */
public interface AbilityCombatBridge {

    boolean applyDamage(Player attacker, LivingEntity target, String style, int maxHit, double xpMultiplier);

    boolean isPvpAllowed(Player attacker, Player victim);

    int currentPrayer(Player player);

    boolean drainPrayer(Player player, int amount);
}
