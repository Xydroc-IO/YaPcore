package com.yapcore.combat.formula;

import com.yapcore.sched.YapSched;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

/** Offloads hit rolls to the async scheduler; apply damage on entity threads. */
public final class CombatHitResolver {

    private CombatHitResolver() {
    }

    @FunctionalInterface
    public interface RollFn extends Function<Random, DamageCalculator.Result> {
    }

    public static void resolveAsync(JavaPlugin plugin, RollFn roll, Consumer<DamageCalculator.Result> onResult) {
        YapSched.async(plugin, () -> {
            DamageCalculator.Result result = roll.apply(ThreadLocalRandom.current());
            onResult.accept(result);
        });
    }
}
