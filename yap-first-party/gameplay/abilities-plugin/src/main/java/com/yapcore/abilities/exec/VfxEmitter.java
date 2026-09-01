package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.sched.YapSched;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Particle + sound emitter with shapes (burst/ring/helix/beam/nova) and
 * dust/block data support for cool cast/hit cosmetics.
 */
public final class VfxEmitter {

    private VfxEmitter() {
    }

    public static void runCast(JavaPlugin plugin, Player caster, AbilityEffect effect) {
        YapSched.entity(plugin, caster, () ->
                emitDirectional(plugin, caster.getEyeLocation(), caster.getLocation().getDirection(), effect));
    }

    public static void runHit(JavaPlugin plugin, Entity anchor, AbilityEffect effect) {
        if (anchor instanceof LivingEntity living) {
            YapSched.entity(plugin, living, () -> emitAt(plugin, living.getLocation().add(0, 1, 0), effect));
        } else {
            YapSched.global(plugin, () -> emitAt(plugin, anchor.getLocation(), effect));
        }
    }

    public static void emitAt(Location location, AbilityEffect effect) {
        emitAt(null, location, effect);
    }

    public static void emitAt(JavaPlugin plugin, Location location, AbilityEffect effect) {
        switch (effect.kind()) {
            case VFX -> spawnShaped(plugin, location, null, effect);
            case SOUND -> playSound(location, effect);
            default -> {
            }
        }
    }

    public static void emitDirectional(JavaPlugin plugin, Location location, Vector direction, AbilityEffect effect) {
        switch (effect.kind()) {
            case VFX -> spawnShaped(plugin, location, direction, effect);
            case SOUND -> playSound(location, effect);
            default -> {
            }
        }
    }

    private static void spawnShaped(JavaPlugin plugin, Location location, Vector direction, AbilityEffect effect) {
        Particle particle = parseParticle(effect.param("particle", "CLOUD"));
        if (particle == null || location.getWorld() == null) {
            return;
        }
        int ticks = effect.intParam("ticks", 1);
        int interval = Math.max(1, effect.intParam("interval", 1));
        if (ticks <= 1 || plugin == null) {
            spawnOnce(location, direction, particle, effect);
            return;
        }
        spawnOnce(location, direction, particle, effect);
        for (int t = interval; t < ticks; t += interval) {
            final int delay = t;
            Location snap = location.clone();
            Vector dirSnap = direction == null ? null : direction.clone();
            YapSched.globalLater(plugin, () -> spawnOnce(snap, dirSnap, particle, effect), delay);
        }
    }

    private static void spawnOnce(Location location, Vector direction, Particle particle, AbilityEffect effect) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        String shape = effect.param("shape", "burst").toLowerCase();
        int count = Math.max(1, effect.intParam("count", 8));
        double spread = effect.doubleParam("spread", 0.25);
        double offsetY = effect.doubleParam("offset-y", 0.5);
        double radius = effect.doubleParam("radius", 1.2);
        double speed = effect.doubleParam("speed", 0.01);
        Location at = location.clone().add(0, offsetY, 0);
        Object data = particleData(particle, effect);

        switch (shape) {
            case "ring" -> spawnRing(world, at, particle, data, count, radius, speed);
            case "helix", "spiral" -> spawnHelix(world, at, particle, data, count, radius, speed);
            case "beam" -> spawnBeam(world, at, direction, particle, data, count, radius, speed);
            case "nova" -> spawnNova(world, at, particle, data, count, radius, speed);
            case "burst" -> spawnBurst(world, at, particle, data, count, spread, speed);
            default -> spawnBurst(world, at, particle, data, count, spread, speed);
        }
    }

    private static void spawnBurst(
            World world, Location at, Particle particle, Object data, int count, double spread, double speed) {
        if (data != null) {
            world.spawnParticle(particle, at, count, spread, spread, spread, speed, data);
        } else {
            world.spawnParticle(particle, at, count, spread, spread, spread, speed);
        }
    }

    private static void spawnRing(
            World world, Location at, Particle particle, Object data, int count, double radius, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = at.clone().add(x, 0, z);
            if (data != null) {
                world.spawnParticle(particle, p, 1, 0, 0, 0, speed, data);
            } else {
                world.spawnParticle(particle, p, 1, 0, 0, 0, speed);
            }
        }
    }

    private static void spawnHelix(
            World world, Location at, Particle particle, Object data, int count, double radius, double speed) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double angle = t * Math.PI * 4;
            double x = Math.cos(angle) * radius * (1.0 - t * 0.3);
            double z = Math.sin(angle) * radius * (1.0 - t * 0.3);
            double y = t * 1.6;
            Location p = at.clone().add(x, y, z);
            if (data != null) {
                world.spawnParticle(particle, p, 1, 0, 0, 0, speed, data);
            } else {
                world.spawnParticle(particle, p, 1, 0, 0, 0, speed);
            }
        }
    }

    private static void spawnBeam(
            World world, Location at, Vector direction, Particle particle, Object data, int count, double length, double speed) {
        Vector dir = direction == null || direction.lengthSquared() < 0.0001
                ? new Vector(0, 0, 1)
                : direction.clone().normalize();
        double step = Math.max(0.25, length / Math.max(1, count));
        for (int i = 0; i < count; i++) {
            Location p = at.clone().add(dir.clone().multiply(i * step));
            if (data != null) {
                world.spawnParticle(particle, p, 1, 0.02, 0.02, 0.02, speed, data);
            } else {
                world.spawnParticle(particle, p, 1, 0.02, 0.02, 0.02, speed);
            }
        }
    }

    private static void spawnNova(
            World world, Location at, Particle particle, Object data, int count, double radius, double speed) {
        spawnRing(world, at, particle, data, Math.max(8, count / 2), radius, speed);
        spawnBurst(world, at, particle, data, Math.max(4, count / 2), radius * 0.35, speed * 2);
    }

    private static Object particleData(Particle particle, AbilityEffect effect) {
        String colorRaw = effect.param("color", "");
        if (!colorRaw.isBlank() && (particle == Particle.DUST || particle.name().contains("DUST"))) {
            Color color = parseColor(colorRaw);
            float size = (float) effect.doubleParam("size", 1.2);
            return new Particle.DustOptions(color, size);
        }
        String blockRaw = effect.param("block", "");
        if (blockRaw.isBlank() && (particle == Particle.BLOCK || particle.name().contains("BLOCK"))) {
            blockRaw = "STONE";
        }
        if (!blockRaw.isBlank()) {
            Material mat = Material.matchMaterial(blockRaw);
            if (mat != null && mat.isBlock()) {
                BlockData bd = mat.createBlockData();
                try {
                    return bd;
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Color parseColor(String raw) {
        String[] parts = raw.split("[, ]+");
        if (parts.length >= 3) {
            try {
                int r = clampColor(Integer.parseInt(parts[0].trim()));
                int g = clampColor(Integer.parseInt(parts[1].trim()));
                int b = clampColor(Integer.parseInt(parts[2].trim()));
                return Color.fromRGB(r, g, b);
            } catch (NumberFormatException ignored) {
            }
        }
        return switch (raw.trim().toLowerCase()) {
            case "fire", "red" -> Color.fromRGB(255, 80, 20);
            case "water", "blue" -> Color.fromRGB(40, 120, 255);
            case "wind", "white" -> Color.fromRGB(220, 240, 255);
            case "earth", "brown" -> Color.fromRGB(120, 80, 40);
            case "arcane", "purple" -> Color.fromRGB(160, 60, 255);
            case "poison", "green" -> Color.fromRGB(80, 200, 60);
            default -> Color.fromRGB(255, 200, 80);
        };
    }

    private static int clampColor(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void playSound(Location location, AbilityEffect effect) {
        Sound sound = parseSound(effect.param("sound", ""));
        if (sound == null || location.getWorld() == null) {
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
            // Aliases for older pack names
            return switch (raw.trim().toUpperCase()) {
                case "BLOCK_CRACK", "BLOCK_DUST" -> Particle.BLOCK;
                case "REDSTONE", "DUST_COLOR" -> Particle.DUST;
                case "SPELL_MOB", "SPELL" -> {
                    try {
                        yield Particle.valueOf("ENTITY_EFFECT");
                    } catch (IllegalArgumentException ex) {
                        yield Particle.DUST;
                    }
                }
                default -> null;
            };
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
