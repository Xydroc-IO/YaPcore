package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VfxEmitter {

    private VfxEmitter() {
    }

    public static void runCast(JavaPlugin plugin, Player caster, AbilityEffect effect) {
        YapSched.entity(plugin, caster, () -> emitAt(caster.getLocation(), effect));
    }

    public static void runHit(JavaPlugin plugin, Entity anchor, AbilityEffect effect) {
        if (anchor instanceof LivingEntity living) {
            YapSched.entity(plugin, living, () -> emitAt(living.getLocation().add(0, 1, 0), effect));
        } else {
            YapSched.global(plugin, () -> emitAt(anchor.getLocation(), effect));
        }
    }

    public static void emitAt(Location location, AbilityEffect effect) {
        switch (effect.kind()) {
            case VFX -> spawnParticle(location, effect);
            case SOUND -> playSound(location, effect);
            default -> {
            }
        }
    }

    private static void spawnParticle(Location location, AbilityEffect effect) {
        Particle particle = parseParticle(effect.param("particle", "CLOUD"));
        if (particle == null) {
            return;
        }
        int count = effect.intParam("count", 8);
        double spread = effect.doubleParam("spread", 0.25);
        double offsetY = effect.doubleParam("offset-y", 0.5);
        Location at = location.clone().add(0, offsetY, 0);
        location.getWorld().spawnParticle(particle, at, count, spread, spread, spread, 0.01);
    }

    private static void playSound(Location location, AbilityEffect effect) {
        Sound sound = parseSound(effect.param("sound", ""));
        if (sound == null) {
            return;
        }
        float volume = (float) effect.doubleParam("volume", 1.0);
        float pitch = (float) effect.doubleParam("pitch", 1.0);
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    private static Particle parseParticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
