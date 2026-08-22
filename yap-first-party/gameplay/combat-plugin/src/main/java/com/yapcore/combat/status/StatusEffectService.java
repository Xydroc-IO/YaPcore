package com.yapcore.combat.status;

import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.mmo.CombatStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatusEffectService {

    private final StatusEffectRegistry registry;
    private final Map<UUID, List<ActiveStatusEffect>> active = new ConcurrentHashMap<>();

    public StatusEffectService(StatusEffectRegistry registry) {
        this.registry = registry;
    }

    public void apply(LivingEntity target, String effectId, LivingEntity source, int stacks) {
        StatusEffectDefinition def = registry.get(effectId);
        if (def == null || stacks <= 0 || target == null) {
            return;
        }
        UUID sourceId = source == null ? null : source.getUniqueId();
        long now = System.currentTimeMillis();
        long durationMs = def.durationSeconds() * 1000L;
        long tickMs = def.tickIntervalSeconds() * 1000L;
        List<ActiveStatusEffect> list = active.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        synchronized (list) {
            for (ActiveStatusEffect existing : list) {
                if (existing.effectId().equals(effectId)) {
                    int nextStacks = Math.min(def.maxStacks(), existing.stacks() + stacks);
                    existing.setStacks(nextStacks);
                    existing.setExpiresAtMs(now + durationMs);
                    return;
                }
            }
            list.add(new ActiveStatusEffect(
                    effectId,
                    Math.min(def.maxStacks(), stacks),
                    now + durationMs,
                    now + tickMs,
                    sourceId));
        }
    }

    public void clear(UUID entityId) {
        active.remove(entityId);
    }

    public List<String> describe(LivingEntity entity) {
        List<ActiveStatusEffect> list = active.get(entity.getUniqueId());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        synchronized (list) {
            for (ActiveStatusEffect effect : list) {
                if (effect.expiresAtMs() <= now) {
                    continue;
                }
                StatusEffectDefinition def = registry.get(effect.effectId());
                String name = def == null ? effect.effectId() : def.displayName();
                long sec = Math.max(1, (effect.expiresAtMs() - now) / 1000);
                out.add(name + " x" + effect.stacks() + " (" + sec + "s)");
            }
        }
        return out;
    }

    public StatusModifiers modifiers(LivingEntity entity) {
        List<ActiveStatusEffect> list = active.get(entity.getUniqueId());
        if (list == null || list.isEmpty()) {
            return StatusModifiers.none();
        }
        long now = System.currentTimeMillis();
        StatusModifiers mods = StatusModifiers.none();
        synchronized (list) {
            for (ActiveStatusEffect effect : list) {
                if (effect.expiresAtMs() <= now) {
                    continue;
                }
                StatusEffectDefinition def = registry.get(effect.effectId());
                if (def == null) {
                    continue;
                }
                int stacks = effect.stacks();
                mods = mods.merge(new StatusModifiers(
                        def.attackModifier() * stacks,
                        def.strengthModifier() * stacks,
                        def.defenceModifier() * stacks,
                        1.0,
                        def.damageTakenMultiplier(),
                        def.movementScale(),
                        def.blocksAttacks()));
            }
        }
        return mods;
    }

    public boolean blocksAttacks(LivingEntity entity) {
        return modifiers(entity).blocksAttacks();
    }

    public int scaleOutgoingDamage(int baseDamage, LivingEntity attacker) {
        StatusModifiers mods = modifiers(attacker);
        return (int) Math.ceil(baseDamage * mods.outgoingDamageMultiplier());
    }

    public int scaleIncomingDamage(int baseDamage, LivingEntity victim) {
        StatusModifiers mods = modifiers(victim);
        return (int) Math.ceil(baseDamage * mods.incomingDamageMultiplier());
    }

    public void tick(LivingEntity entity, CombatServiceImpl combat) {
        List<ActiveStatusEffect> list = active.get(entity.getUniqueId());
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            list.removeIf(e -> e.expiresAtMs() <= now);
            for (ActiveStatusEffect effect : list) {
                StatusEffectDefinition def = registry.get(effect.effectId());
                if (def == null || effect.nextTickAtMs() > now) {
                    continue;
                }
                effect.setNextTickAtMs(now + def.tickIntervalSeconds() * 1000L);
                int stacks = effect.stacks();
                if (def.damagePerTick() > 0 && entity instanceof Player player) {
                    int dot = def.damagePerTick() * stacks;
                    boolean dead = combat.applyDamage(player, dot, CombatStyle.MAGIC);
                    if (dead) {
                        player.setHealth(0);
                    }
                } else if (def.damagePerTick() > 0) {
                    double next = Math.max(0, entity.getHealth() - def.damagePerTick() * stacks);
                    entity.setHealth(next);
                }
                if (def.healPerTick() > 0 && entity instanceof Player player) {
                    combat.heal(player, def.healPerTick() * stacks);
                }
                applyMovement(entity, def);
            }
        }
        if (list.isEmpty()) {
            active.remove(entity.getUniqueId());
        }
    }

    private void applyMovement(LivingEntity entity, StatusEffectDefinition def) {
        if (def.movementScale() >= 1.0 || def.kind() != StatusEffectKind.CROWD_CONTROL) {
            return;
        }
        var velocity = entity.getVelocity();
        entity.setVelocity(velocity.multiply(def.movementScale()));
    }

    public Collection<UUID> activeEntityIds() {
        return java.util.Set.copyOf(active.keySet());
    }

    public Collection<ActiveStatusEffect> snapshot(LivingEntity entity) {
        List<ActiveStatusEffect> list = active.get(entity.getUniqueId());
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }
}
