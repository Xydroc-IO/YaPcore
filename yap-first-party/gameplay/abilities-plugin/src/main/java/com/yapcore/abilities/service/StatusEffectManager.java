package com.yapcore.abilities.service;

import com.yapcore.abilities.ActiveStatusEffect;
import com.yapcore.abilities.StatModifiers;
import com.yapcore.abilities.StatusEffectDefinition;
import com.yapcore.abilities.StatusEffectService;
import com.yapcore.abilities.exec.EffectRunner;
import com.yapcore.abilities.load.StatusEffectPackLoader;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatusEffectManager implements StatusEffectService {

    private final JavaPlugin plugin;
    private final StatusEffectPackLoader loader;
    private EffectRunner effectRunner;
    private YapTask statusTicker;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, MutableEffect>> active = new ConcurrentHashMap<>();

    public StatusEffectManager(JavaPlugin plugin, StatusEffectPackLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    public void attachEffectRunner(EffectRunner effectRunner) {
        this.effectRunner = effectRunner;
    }

    public void startTicker(long intervalTicks) {
        stopTicker();
        statusTicker = YapSched.globalTimer(plugin, this::tickAll, intervalTicks, intervalTicks);
    }

    public void stopTicker() {
        if (statusTicker != null) {
            statusTicker.cancel();
            statusTicker = null;
        }
    }

    @Override
    public Collection<StatusEffectDefinition> definitions() {
        return loader.effects().values();
    }

    @Override
    public Optional<StatusEffectDefinition> get(String id) {
        return Optional.ofNullable(loader.get(id));
    }

    @Override
    public void apply(LivingEntity target, String effectId, UUID sourceId, int stacks) {
        StatusEffectDefinition def = loader.get(effectId);
        if (def == null) {
            return;
        }
        YapSched.entity(plugin, target, () -> {
            UUID id = target.getUniqueId();
            ConcurrentHashMap<String, MutableEffect> map = active.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
            if (!def.group().isBlank()) {
                map.entrySet().removeIf(e -> {
                    StatusEffectDefinition other = loader.get(e.getKey());
                    return other != null && def.group().equals(other.group()) && !other.id().equals(def.id());
                });
            }
            long expires = nowTick() + def.durationTicks();
            MutableEffect existing = map.get(effectId);
            int nextStacks = Math.min(def.maxStacks(), (existing == null ? 0 : existing.stacks) + Math.max(1, stacks));
            map.put(effectId, new MutableEffect(sourceId, nextStacks, expires, nowTick()));
            if (target instanceof org.bukkit.entity.Player player) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        (def.kind() == com.yapcore.abilities.StatusKind.BUFF ? "§a+" : "§c-")
                                + def.displayName() + " §7(" + nextStacks + ")"));
            }
        });
    }

    @Override
    public void remove(LivingEntity target, String effectId) {
        YapSched.entity(plugin, target, () -> {
            ConcurrentHashMap<String, MutableEffect> map = active.get(target.getUniqueId());
            if (map != null) {
                map.remove(effectId);
            }
        });
    }

    @Override
    public List<ActiveStatusEffect> active(UUID entityId) {
        ConcurrentHashMap<String, MutableEffect> map = active.get(entityId);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<ActiveStatusEffect> out = new ArrayList<>();
        for (Map.Entry<String, MutableEffect> entry : map.entrySet()) {
            out.add(new ActiveStatusEffect(entry.getKey(), entry.getValue().sourceId,
                    entry.getValue().stacks, entry.getValue().expiresAtTick));
        }
        return List.copyOf(out);
    }

    @Override
    public void clearAll(LivingEntity target) {
        active.remove(target.getUniqueId());
    }

    @Override
    public StatModifiers aggregateModifiers(LivingEntity entity) {
        ConcurrentHashMap<String, MutableEffect> map = active.get(entity.getUniqueId());
        if (map == null || map.isEmpty()) {
            return StatModifiers.empty();
        }
        int attack = 0;
        int strength = 0;
        int defence = 0;
        int ranged = 0;
        int magic = 0;
        double speed = 1.0;
        double taken = 1.0;
        for (Map.Entry<String, MutableEffect> entry : map.entrySet()) {
            StatusEffectDefinition def = loader.get(entry.getKey());
            if (def == null) {
                continue;
            }
            int stacks = entry.getValue().stacks;
            StatModifiers m = def.modifiers();
            attack += m.attackBoost() * stacks;
            strength += m.strengthBoost() * stacks;
            defence += m.defenceBoost() * stacks;
            ranged += m.rangedBoost() * stacks;
            magic += m.magicBoost() * stacks;
            speed *= Math.pow(m.speedMultiplier(), stacks);
            taken *= Math.pow(m.damageTakenMultiplier(), stacks);
        }
        return new StatModifiers(attack, strength, defence, ranged, magic, speed, taken);
    }

    private void tickAll() {
        long now = nowTick();
        for (Map.Entry<UUID, ConcurrentHashMap<String, MutableEffect>> entityEntry : active.entrySet()) {
            LivingEntity entity = findEntity(entityEntry.getKey());
            if (entity == null || !entity.isValid()) {
                active.remove(entityEntry.getKey());
                continue;
            }
            for (Map.Entry<String, MutableEffect> effectEntry : entityEntry.getValue().entrySet()) {
                StatusEffectDefinition def = loader.get(effectEntry.getKey());
                MutableEffect mutable = effectEntry.getValue();
                if (def == null) {
                    continue;
                }
                if (now >= mutable.expiresAtTick) {
                    if (effectRunner != null) {
                        YapSched.entity(plugin, entity, () -> effectRunner.runAmbient(entity, def.expireEffects(), null));
                    }
                    entityEntry.getValue().remove(effectEntry.getKey());
                    continue;
                }
                if (!def.tickEffects().isEmpty()
                        && (now - mutable.lastTickAt) >= def.tickInterval()) {
                    mutable.lastTickAt = now;
                    if (effectRunner != null) {
                        YapSched.entity(plugin, entity, () -> effectRunner.runAmbient(entity, def.tickEffects(), null));
                    }
                }
            }
        }
    }

    private LivingEntity findEntity(UUID id) {
        return plugin.getServer().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /** Folia-safe tick clock — {@link Bukkit#getCurrentTick()} throws off a region thread. */
    private static long nowTick() {
        return System.currentTimeMillis() / 50L;
    }

    private static final class MutableEffect {
        private final UUID sourceId;
        private final int stacks;
        private final long expiresAtTick;
        private long lastTickAt;

        private MutableEffect(UUID sourceId, int stacks, long expiresAtTick, long lastTickAt) {
            this.sourceId = sourceId;
            this.stacks = stacks;
            this.expiresAtTick = expiresAtTick;
            this.lastTickAt = lastTickAt;
        }
    }
}
