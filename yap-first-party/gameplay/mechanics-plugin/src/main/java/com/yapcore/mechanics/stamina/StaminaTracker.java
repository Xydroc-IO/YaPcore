package com.yapcore.mechanics.stamina;

import com.yapcore.mechanics.MechanicsConfig;
import com.yapcore.mechanics.StaminaState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaminaTracker {

    private final MechanicsConfig config;
    private final Map<UUID, Double> stamina = new ConcurrentHashMap<>();

    public StaminaTracker(MechanicsConfig config) {
        this.config = config;
    }

    public StaminaState state(UUID uuid) {
        double max = config.staminaMax();
        double cur = stamina.getOrDefault(uuid, max);
        return new StaminaState(cur, max, cur <= 0.01);
    }

    public boolean consume(UUID uuid, double amount) {
        if (!config.staminaEnabled() || amount <= 0) {
            return true;
        }
        double max = config.staminaMax();
        double cur = stamina.getOrDefault(uuid, max);
        if (cur < amount) {
            stamina.put(uuid, 0.0);
            return false;
        }
        stamina.put(uuid, cur - amount);
        return true;
    }

    public void regen(UUID uuid, double amount) {
        if (!config.staminaEnabled() || amount <= 0) {
            return;
        }
        double max = config.staminaMax();
        double cur = stamina.getOrDefault(uuid, max);
        stamina.put(uuid, Math.min(max, cur + amount));
    }

    public void tickRegenAll(Iterable<? extends org.bukkit.entity.Player> players) {
        if (!config.staminaEnabled()) {
            return;
        }
        double amount = config.staminaRegen() / 20.0;
        for (org.bukkit.entity.Player player : players) {
            regen(player.getUniqueId(), amount);
        }
    }

    public void tickSprintDrain(Iterable<? extends org.bukkit.entity.Player> players) {
        if (!config.staminaEnabled() || config.sprintDrain() <= 0) {
            return;
        }
        double amount = config.sprintDrain() / 20.0;
        for (org.bukkit.entity.Player player : players) {
            if (player.isSprinting() && player.getVelocity().lengthSquared() > 0.01) {
                consume(player.getUniqueId(), amount);
            }
        }
    }

    public void reset(UUID uuid) {
        stamina.put(uuid, config.staminaMax());
    }
}
