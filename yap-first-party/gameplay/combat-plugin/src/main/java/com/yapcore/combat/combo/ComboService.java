package com.yapcore.combat.combo;

import com.yapcore.combat.CombatConfig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ComboService {

    private final CombatConfig.ComboConfig config;
    private final Map<UUID, ComboState> combos = new ConcurrentHashMap<>();

    public ComboService(CombatConfig.ComboConfig config) {
        this.config = config;
    }

    public HitResult recordHit(Player attacker, LivingEntity target, int baseDamage) {
        return recordHit(attacker.getUniqueId(), target.getUniqueId(), baseDamage);
    }

    public HitResult recordHit(UUID attackerId, UUID targetId, int baseDamage) {
        if (!config.enabled() || baseDamage <= 0) {
            return new HitResult(baseDamage, 1, 0.0, "");
        }
        long now = System.currentTimeMillis();
        ComboState state = combos.compute(attackerId, (id, prev) -> {
            if (prev == null || now - prev.lastHitAtMs() > config.windowMs()) {
                return new ComboState(1, now, targetId);
            }
            int next = prev.count() + 1;
            if (next > config.maxCombo()) {
                next = config.maxCombo();
            }
            return new ComboState(next, now, targetId);
        });
        double multiplier = multiplierFor(state);
        int finalDamage = Math.max(1, (int) Math.ceil(baseDamage * multiplier));
        return new HitResult(finalDamage, state.count(), multiplier, format(state, multiplier));
    }

    public void recordMiss(Player attacker) {
        recordMiss(attacker.getUniqueId());
    }

    public void recordMiss(UUID attackerId) {
        if (config.resetOnMiss()) {
            combos.remove(attackerId);
        }
    }

    public void reset(Player attacker) {
        reset(attacker.getUniqueId());
    }

    public void reset(UUID attackerId) {
        combos.remove(attackerId);
    }

    public int currentCombo(Player attacker) {
        return currentCombo(attacker.getUniqueId());
    }

    public int currentCombo(UUID attackerId) {
        ComboState state = combos.get(attackerId);
        if (state == null) {
            return 0;
        }
        if (System.currentTimeMillis() - state.lastHitAtMs() > config.windowMs()) {
            combos.remove(attackerId);
            return 0;
        }
        return state.count();
    }

    public double currentMultiplier(Player attacker) {
        return currentMultiplier(attacker.getUniqueId());
    }

    public double currentMultiplier(UUID attackerId) {
        ComboState state = combos.get(attackerId);
        if (state == null) {
            return 1.0;
        }
        if (System.currentTimeMillis() - state.lastHitAtMs() > config.windowMs()) {
            combos.remove(attackerId);
            return 1.0;
        }
        return multiplierFor(state);
    }

    private double multiplierFor(ComboState state) {
        double bonus = Math.max(0, state.count() - 1) * config.bonusPerStack();
        return 1.0 + bonus;
    }

    private String format(ComboState state, double multiplier) {
        if (state.count() <= 1) {
            return "";
        }
        int pct = (int) Math.round((multiplier - 1.0) * 100);
        return "§e" + state.count() + "x COMBO §7+" + pct + "%";
    }

    private record ComboState(int count, long lastHitAtMs, UUID targetId) {
    }

    public record HitResult(int damage, int comboCount, double multiplier, String label) {
    }
}
