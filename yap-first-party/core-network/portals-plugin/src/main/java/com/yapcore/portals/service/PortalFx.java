package com.yapcore.portals.service;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalCuboid;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cinematic portal audio + one-shot enter/arrive bursts.
 * Custom pack sounds ({@code yap:portal.*}) play when the YaP default pack is loaded;
 * vanilla layers always fire as a fallback.
 */
final class PortalFx {

    static final String SOUND_WARP = "yap:portal.warp";
    static final String SOUND_ARRIVE = "yap:portal.arrive";
    static final String SOUND_HUM = "yap:portal.hum";

    private PortalFx() {
    }

    static void playWarp(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        playCustom(player, loc, SOUND_WARP, 1.0f, 1.0f);
        // Vanilla layers so clients without the pack still hear a warp.
        safeSound(player, loc, Sound.BLOCK_PORTAL_TRAVEL, 0.85f, 1.15f);
        safeSound(player, loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 0.85f);
        safeSound(player, loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 0.75f);
    }

    static void playArrive(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        playCustom(player, loc, SOUND_ARRIVE, 0.9f, 1.05f);
        safeSound(player, loc, Sound.BLOCK_PORTAL_TRIGGER, 0.45f, 1.4f);
        safeSound(player, loc, Sound.ENTITY_PLAYER_LEVELUP, 0.25f, 1.8f);
    }

    static void playHumNear(World world, Portal portal, double radius) {
        if (world == null || portal == null) {
            return;
        }
        PortalCuboid box = portal.cuboid();
        Location mid = new Location(world,
                (box.minX() + box.maxX()) * 0.5 + 0.5,
                (box.minY() + box.maxY()) * 0.5 + 0.5,
                (box.minZ() + box.maxZ()) * 0.5 + 0.5);
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(mid) > radius * radius) {
                continue;
            }
            playCustom(viewer, mid, SOUND_HUM, 0.22f, 0.9f
                    + 0.05f * ThreadLocalRandom.current().nextFloat());
        }
    }

    static void enterBurst(World world, Player player, Portal portal) {
        if (world == null || player == null || portal == null) {
            return;
        }
        Color color = Color.fromRGB(
                PortalColors.red(portal.color()),
                PortalColors.green(portal.color()),
                PortalColors.blue(portal.color()));
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        Particle.DustOptions bright = new Particle.DustOptions(
                Color.fromRGB(
                        Math.min(255, color.getRed() + 80),
                        Math.min(255, color.getGreen() + 80),
                        Math.min(255, color.getBlue() + 80)),
                1.1f);
        Location at = player.getLocation().add(0, 1.0, 0);
        world.spawnParticle(Particle.DUST, at, 48, 0.55, 0.9, 0.55, 0.02, dust);
        world.spawnParticle(Particle.DUST, at, 24, 0.35, 0.6, 0.35, 0.01, bright);
        world.spawnParticle(Particle.PORTAL, at, 80, 0.4, 0.8, 0.4, 0.9);
        world.spawnParticle(Particle.REVERSE_PORTAL, at, 40, 0.25, 0.5, 0.25, 0.15);
        world.spawnParticle(Particle.END_ROD, at, 18, 0.4, 0.7, 0.4, 0.08);
        try {
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
        } catch (IllegalArgumentException ignored) {
            // some Paper builds require particle data for FLASH
        }
    }

    static void arriveBurst(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location at = player.getLocation().add(0, 1.0, 0);
        world.spawnParticle(Particle.END_ROD, at, 24, 0.45, 0.7, 0.45, 0.05);
        world.spawnParticle(Particle.PORTAL, at, 50, 0.35, 0.6, 0.35, 0.55);
        world.spawnParticle(Particle.REVERSE_PORTAL, at, 20, 0.2, 0.4, 0.2, 0.08);
        try {
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
        } catch (IllegalArgumentException ignored) {
            // some Paper builds require particle data for FLASH
        }
    }

    private static void playCustom(Player player, Location loc, String key, float volume, float pitch) {
        try {
            player.playSound(loc, key, SoundCategory.PLAYERS, volume, pitch);
        } catch (Exception ignored) {
            // custom pack sound optional
        }
    }

    private static void safeSound(Player player, Location loc, Sound sound, float volume, float pitch) {
        try {
            player.playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch);
        } catch (Exception ignored) {
            // sound optional
        }
    }
}
