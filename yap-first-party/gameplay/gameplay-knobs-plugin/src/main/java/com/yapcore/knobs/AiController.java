package com.yapcore.knobs;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Applies deep AI knobs via Paper {@link MobGoals} / {@link VanillaGoal}. */
public final class AiController {

    private static final Map<String, GoalKey<?>> VANILLA = new ConcurrentHashMap<>();
    private static volatile boolean indexed;

    private AiController() {
    }

    public static void apply(Mob mob, KnobsConfig.MobKnobs knobs, Logger log) {
        if (knobs == null || !knobs.enabled()) {
            return;
        }
        mob.setAware(knobs.aware());
        MobGoals goals = Bukkit.getMobGoals();
        if (knobs.disableAi()) {
            goals.removeAllGoals(mob);
            return;
        }
        if (knobs.disableRandomStroll()) {
            removeByType(goals, mob, GoalType.MOVE);
            // re-add nothing — stroll gone; targeting may remain
        }
        if (knobs.disablePanic()) {
            removeNamed(goals, mob, "PANIC", log);
        }
        for (String name : knobs.removeGoals()) {
            removeNamed(goals, mob, name, log);
        }
    }

    public static void clearMoveWhileRidden(Mob mob) {
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.MOVE);
    }

    private static void removeByType(MobGoals goals, Mob mob, GoalType type) {
        goals.removeAllGoals(mob, type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeNamed(MobGoals goals, Mob mob, String name, Logger log) {
        ensureIndex();
        GoalKey key = VANILLA.get(name.toUpperCase(Locale.ROOT).replace('-', '_'));
        if (key == null) {
            if (log != null) {
                log.fine("Unknown VanillaGoal: " + name);
            }
            return;
        }
        try {
            goals.removeGoal(mob, key);
        } catch (ClassCastException ex) {
            // GoalKey typed to a different mob class — ignore
        }
    }

    private static void ensureIndex() {
        if (indexed) {
            return;
        }
        synchronized (AiController.class) {
            if (indexed) {
                return;
            }
            for (Field f : VanillaGoal.class.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!GoalKey.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    Object v = f.get(null);
                    if (v instanceof GoalKey<?> gk) {
                        VANILLA.put(f.getName(), gk);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            indexed = true;
        }
    }
}
