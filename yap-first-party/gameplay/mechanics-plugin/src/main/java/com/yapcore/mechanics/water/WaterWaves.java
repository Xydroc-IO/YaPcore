package com.yapcore.mechanics.water;

import com.yapcore.mechanics.MechanicsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Server-side wave feel: surface bobbing, entry splash, foam particles.
 * Complements YaP Shaders client visuals (works for vanilla clients too).
 */
public final class WaterWaves {

    private final JavaPlugin plugin;
    private final MechanicsConfig config;
    private long tick;

    public WaterWaves(JavaPlugin plugin, MechanicsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void tick(Iterable<? extends Player> players) {
        if (!config.waterWavesEnabled()) {
            return;
        }
        tick++;
        double amp = config.waterWaveAmplitude();
        double speed = config.waterWaveSpeed();
        for (Player player : players) {
            if (!player.isOnline()) {
                continue;
            }
            player.getScheduler().run(plugin, st -> applyToPlayer(player, amp, speed), null);
        }
    }

    private void applyToPlayer(Player player, double amp, double speed) {
        boolean inFluid = player.isInWater() || player.isSwimming() || isWaterSurface(player.getLocation());
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Boat boat) {
            bobEntity(boat, amp * 1.25, speed);
            maybeFoam(boat.getLocation(), 0.35);
            return;
        }
        if (!inFluid) {
            return;
        }
        // Standing/swimming in water — gentle heave
        if (player.isOnGround() || player.isSwimming() || player.isInWater()) {
            bobEntity(player, amp, speed);
            if ((tick & 3L) == 0L) {
                maybeFoam(player.getLocation(), player.isSwimming() ? 0.55 : 0.25);
            }
        }
    }

    private void bobEntity(Entity entity, double amp, double speed) {
        Location loc = entity.getLocation();
        if (!isNearWater(loc)) {
            return;
        }
        double phase = (loc.getX() * 0.55 + loc.getZ() * 0.42) + tick * speed * 0.12;
        double heave = Math.sin(phase) * amp;
        // Small horizontal sway so waves feel directional
        double swayX = Math.cos(phase * 0.7) * amp * 0.15;
        double swayZ = Math.sin(phase * 0.55) * amp * 0.15;

        Vector v = entity.getVelocity();
        // Don't fight strong player input / falls
        if (Math.abs(v.getY()) > 0.55) {
            return;
        }
        v.setX(v.getX() * 0.98 + swayX * 0.08);
        v.setZ(v.getZ() * 0.98 + swayZ * 0.08);
        v.setY(v.getY() * 0.85 + heave * 0.22);
        entity.setVelocity(v);
    }

    public void splash(LivingEntity entity) {
        if (!config.waterWavesEnabled() || !config.waterSplashEnabled()) {
            return;
        }
        Location loc = entity.getLocation().add(0, 0.2, 0);
        entity.getWorld().spawnParticle(Particle.SPLASH, loc, 28, 0.35, 0.15, 0.35, 0.08);
        entity.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 12, 0.25, 0.1, 0.25, 0.02);
        entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.55f, 1.05f);
    }

    private void maybeFoam(Location loc, double chance) {
        if (Math.random() > chance) {
            return;
        }
        Location at = loc.clone().add(0, 0.05, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.4, 0.02, 0.4, 0.001);
        loc.getWorld().spawnParticle(Particle.BUBBLE, at, 3, 0.3, 0.05, 0.3, 0.01);
    }

    private static boolean isNearWater(Location loc) {
        Block feet = loc.getBlock();
        Block below = loc.clone().add(0, -0.2, 0).getBlock();
        return isWatery(feet) || isWatery(below) || isWatery(loc.clone().add(0, 0.9, 0).getBlock());
    }

    private static boolean isWaterSurface(Location loc) {
        Block b = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();
        return isWatery(b) || isWatery(below);
    }

    private static boolean isWatery(Block block) {
        Material t = block.getType();
        if (t == Material.WATER || t == Material.BUBBLE_COLUMN) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
    }
}
