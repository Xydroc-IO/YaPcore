package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Impact weight: short velocity jitter pulses for cast kick / hit shake.
 * Folia-safe — only mutates entities on their own region scheduler.
 */
public final class ImpactFx {

    private ImpactFx() {
    }

    public static void run(JavaPlugin plugin, LivingEntity anchor, AbilityEffect effect) {
        if (plugin == null || anchor == null || effect == null) {
            return;
        }
        double power = effect.doubleParam("power", 0.14);
        int pulses = Math.max(1, effect.intParam("pulses", 3));
        int interval = Math.max(1, effect.intParam("interval", 1));
        double radius = effect.doubleParam("radius", 0);
        if (radius > 0.1) {
            shakeArea(plugin, anchor.getLocation(), power, pulses, interval, radius, anchor);
        } else {
            shakeEntity(plugin, anchor, power, pulses, interval);
        }
    }

    public static void shakeAt(JavaPlugin plugin, Location at, double power, int pulses, double radius) {
        if (plugin == null || at == null || at.getWorld() == null) {
            return;
        }
        shakeArea(plugin, at, power, Math.max(1, pulses), 1, Math.max(0.5, radius), null);
    }

    private static void shakeArea(
            JavaPlugin plugin,
            Location at,
            double power,
            int pulses,
            int interval,
            double radius,
            LivingEntity prefer) {
        YapSched.region(plugin, at, () -> {
            if (prefer instanceof Player player && player.isOnline()) {
                shakeEntity(plugin, player, power, pulses, interval);
            }
            for (Entity nearby : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
                if (!(nearby instanceof Player player) || !player.isOnline()) {
                    continue;
                }
                if (prefer != null && player.getUniqueId().equals(prefer.getUniqueId())) {
                    continue;
                }
                shakeEntity(plugin, player, power * 0.75, pulses, interval);
            }
        });
    }

    private static void shakeEntity(JavaPlugin plugin, LivingEntity entity, double power, int pulses, int interval) {
        YapSched.entity(plugin, entity, () -> {
            applyPulse(entity, power);
            for (int i = 1; i < pulses; i++) {
                final int delay = i * interval;
                final double pulsePower = power * (1.0 - (i / (double) (pulses + 1)));
                YapSched.entityLater(plugin, entity, () -> {
                    if (entity.isValid() && (!(entity instanceof Player p) || p.isOnline())) {
                        applyPulse(entity, pulsePower);
                    }
                }, delay);
            }
        });
    }

    private static void applyPulse(LivingEntity entity, double power) {
        double p = Math.max(0.02, Math.min(0.45, power));
        Vector jitter = new Vector(
                (Math.random() - 0.5) * p * 2,
                Math.random() * p * 0.35,
                (Math.random() - 0.5) * p * 2);
        entity.setVelocity(entity.getVelocity().add(jitter));
    }
}
