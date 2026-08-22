package com.yapcore.combat.model;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerCombatState {

    private final UUID playerId;
    private int currentHp;
    private int currentPrayer;
    private long lastFoodTick;
    private long buffAttackUntil;
    private long buffStrengthUntil;
    private long buffDefenceUntil;
    private final Set<String> activePrayers = new LinkedHashSet<>();
    private final Map<String, Long> potionCooldowns = new HashMap<>();

    public PlayerCombatState(
            UUID playerId,
            int currentHp,
            int currentPrayer,
            long lastFoodTick,
            long buffAttackUntil,
            long buffStrengthUntil,
            long buffDefenceUntil,
            Set<String> activePrayers,
            Map<String, Long> potionCooldowns) {
        this.playerId = playerId;
        this.currentHp = currentHp;
        this.currentPrayer = currentPrayer;
        this.lastFoodTick = lastFoodTick;
        this.buffAttackUntil = buffAttackUntil;
        this.buffStrengthUntil = buffStrengthUntil;
        this.buffDefenceUntil = buffDefenceUntil;
        if (activePrayers != null) {
            this.activePrayers.addAll(activePrayers);
        }
        if (potionCooldowns != null) {
            this.potionCooldowns.putAll(potionCooldowns);
        }
    }

    public static PlayerCombatState fresh(UUID playerId, int maxHp, int maxPrayer) {
        return new PlayerCombatState(
                playerId, maxHp, maxPrayer, 0, 0, 0, 0, Set.of(), Map.of());
    }

    public UUID playerId() {
        return playerId;
    }

    public int currentHp() {
        return currentHp;
    }

    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }

    public int currentPrayer() {
        return currentPrayer;
    }

    public void setCurrentPrayer(int currentPrayer) {
        this.currentPrayer = currentPrayer;
    }

    public Set<String> activePrayers() {
        return Set.copyOf(activePrayers);
    }

    public void setActivePrayers(Set<String> prayers) {
        activePrayers.clear();
        if (prayers != null) {
            activePrayers.addAll(prayers);
        }
    }

    public void togglePrayer(String prayerId, boolean enabled) {
        if (enabled) {
            activePrayers.add(prayerId);
        } else {
            activePrayers.remove(prayerId);
        }
    }

    public long lastFoodTick() {
        return lastFoodTick;
    }

    public void setLastFoodTick(long lastFoodTick) {
        this.lastFoodTick = lastFoodTick;
    }

    public long buffAttackUntil() {
        return buffAttackUntil;
    }

    public void setBuffAttackUntil(long buffAttackUntil) {
        this.buffAttackUntil = buffAttackUntil;
    }

    public long buffStrengthUntil() {
        return buffStrengthUntil;
    }

    public void setBuffStrengthUntil(long buffStrengthUntil) {
        this.buffStrengthUntil = buffStrengthUntil;
    }

    public long buffDefenceUntil() {
        return buffDefenceUntil;
    }

    public void setBuffDefenceUntil(long buffDefenceUntil) {
        this.buffDefenceUntil = buffDefenceUntil;
    }

    public Map<String, Long> potionCooldowns() {
        return potionCooldowns;
    }
}
