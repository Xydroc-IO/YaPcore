package com.yapcore.portals.service;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalCuboid;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Colored walk-up portals using YaP resource-pack textures.
 * <p>
 * The walk-up is colored stained glass (resource-pack overlays). One item display is
 * the portal disc for Java; Bedrock sees the glass volume (displays are not translated).
 * Ambient FX: swirling dust, suction portal, end-rod sparks, rim ring, soft hum.
 * Players trigger on standing in or adjacent to the volume (glass is solid).
 */
public final class PortalVisuals {

    static final String DISPLAY_TAG = "yap_portal_display";
    /** Ambient hum about every 2 seconds at the current particle period. */
    private static final int HUM_EVERY_N_TICKS = 4;

    private final JavaPlugin plugin;
    private final AtomicLong tick = new AtomicLong();

    public PortalVisuals(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyAll(Iterable<Portal> portals) {
        for (Portal portal : portals) {
            if (portal.enabled()) {
                fill(portal);
            } else {
                clear(portal);
            }
        }
    }

    public void fill(Portal portal) {
        paint(portal, true);
    }

    public void clear(Portal portal) {
        paint(portal, false);
    }

    public void tickParticles(Iterable<Portal> portals) {
        long pulse = tick.incrementAndGet();
        boolean hum = pulse % HUM_EVERY_N_TICKS == 0;
        for (Portal portal : portals) {
            if (!portal.enabled()) {
                continue;
            }
            World world = Bukkit.getWorld(portal.world());
            if (world == null) {
                continue;
            }
            PortalCuboid box = portal.cuboid();
            int midX = (box.minX() + box.maxX()) >> 1;
            int midZ = (box.minZ() + box.maxZ()) >> 1;
            Color color = Color.fromRGB(
                    PortalColors.red(portal.color()),
                    PortalColors.green(portal.color()),
                    PortalColors.blue(portal.color()));
            float spin = (float) ((pulse % 10) * (Math.PI * 2.0 / 10.0));
            YapSched.region(plugin, world, midX, midZ, () -> {
                spawnParticles(world, portal, color, pulse);
                if (hum) {
                    PortalFx.playHumNear(world, portal, 14.0);
                }
            });
            // One disc for the whole opening. Older builds left a disc on every
            // block; those stay in unloaded chunks and stack into a shredded curtain.
            maintainDisplays(world, portal, spin);
        }
    }

    /** One-shot cinematic burst when a player commits to a transfer. */
    public void playEnter(Player player, Portal portal) {
        if (player == null || portal == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        PortalFx.enterBurst(world, player, portal);
        PortalFx.playWarp(player);
    }

    /** Destination-side whoosh after Connect lands. */
    public void playArrive(Player player) {
        PortalFx.arriveBurst(player);
        PortalFx.playArrive(player);
    }

    private void spawnParticles(World world, Portal portal, Color color, long pulse) {
        PortalCuboid box = portal.cuboid();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.25f);
        Particle.DustOptions bright = new Particle.DustOptions(
                Color.fromRGB(
                        Math.min(255, color.getRed() + 70),
                        Math.min(255, color.getGreen() + 70),
                        Math.min(255, color.getBlue() + 90)),
                0.95f);
        int estimate = portal.shape().estimateCount(box);
        int count = Math.min(8, Math.max(2, estimate / 10));
        double midX = (box.minX() + box.maxX()) * 0.5 + 0.5;
        double midY = (box.minY() + box.maxY()) * 0.5 + 0.5;
        double midZ = (box.minZ() + box.maxZ()) * 0.5 + 0.5;
        double phase = pulse * 0.35;

        int placed = 0;
        int tries = 0;
        while (placed < count && tries < count * 8) {
            tries++;
            int x = rng.nextInt(box.minX(), box.maxX() + 1);
            int y = rng.nextInt(box.minY(), box.maxY() + 1);
            int z = rng.nextInt(box.minZ(), box.maxZ() + 1);
            if (!portal.containsBlock(x, y, z)) {
                continue;
            }
            double curl = x * 0.73 + z * 0.41 + y * 0.19 + phase;
            Location loc = new Location(world,
                    x + 0.5 + Math.cos(curl) * 0.22,
                    y + 0.2 + rng.nextDouble() * 0.65,
                    z + 0.5 + Math.sin(curl) * 0.22);
            world.spawnParticle(Particle.DUST, loc, 1, 0.04, 0.08, 0.04, 0.0, dust);
            placed++;
        }

        // Orbiting rim ring in the midplane — reads as a movie-portal halo.
        int ring = Math.min(10, Math.max(6, (box.maxX() - box.minX() + box.maxZ() - box.minZ()) / 2 + 4));
        double radius = Math.max(0.6, Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) * 0.45 + 0.35);
        for (int i = 0; i < ring; i++) {
            double a = phase * 0.55 + (i * Math.PI * 2.0) / ring;
            double px = midX + Math.cos(a) * radius;
            double pz = midZ + Math.sin(a) * radius;
            int bx = (int) Math.floor(px);
            int by = (int) Math.floor(midY);
            int bz = (int) Math.floor(pz);
            if (!portal.containsBlock(bx, by, bz)
                    && !portal.containsBlock(bx, box.minY(), bz)
                    && !portal.containsBlock(bx, box.maxY(), bz)) {
                // Keep the halo close: if the ring lands outside the mask, pull inward.
                px = midX + Math.cos(a) * (radius * 0.55);
                pz = midZ + Math.sin(a) * (radius * 0.55);
            }
            Location rim = new Location(world, px, midY + Math.sin(a * 2.0 + phase) * 0.15, pz);
            world.spawnParticle(Particle.DUST, rim, 1, 0.01, 0.02, 0.01, 0.0, bright);
        }

        // Vertical lift sparks from the floor of the pad.
        int lifts = Math.min(2, Math.max(1, estimate / 16));
        for (int i = 0; i < lifts; i++) {
            int x = rng.nextInt(box.minX(), box.maxX() + 1);
            int z = rng.nextInt(box.minZ(), box.maxZ() + 1);
            int y = box.minY();
            if (!portal.containsBlock(x, y, z)) {
                continue;
            }
            Location base = new Location(world, x + 0.5, y + 0.1, z + 0.5);
            world.spawnParticle(Particle.END_ROD, base, 1, 0.05, 0.35, 0.05, 0.02);
        }
    }

    private void paint(Portal portal, boolean place) {
        World world = Bukkit.getWorld(portal.world());
        if (world == null) {
            return;
        }
        PortalCuboid box = portal.cuboid();
        int minX = box.minX() - 1;
        int maxX = box.maxX() + 1;
        int minY = Math.max(world.getMinHeight(), box.minY() - 1);
        int maxY = Math.min(world.getMaxHeight() - 1, box.maxY() + 1);
        int minZ = box.minZ() - 1;
        int maxZ = box.maxZ() + 1;
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add((((long) (x >> 4)) << 32) | ((z >> 4) & 0xffffffffL));
            }
        }
        double faceX = (box.minX() + box.maxX() + 1) / 2.0;
        double faceZ = (box.minZ() + box.maxZ() + 1) / 2.0;
        int faceCx = (int) Math.floor(faceX) >> 4;
        int faceCz = (int) Math.floor(faceZ) >> 4;
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                clearDisplaysInChunk(world, cx, cz, true);
                for (int x = minX; x <= maxX; x++) {
                    if ((x >> 4) != cx) {
                        continue;
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        if ((z >> 4) != cz) {
                            continue;
                        }
                        for (int y = minY; y <= maxY; y++) {
                            Block block = world.getBlockAt(x, y, z);
                            Material type = block.getType();
                            boolean inBox = box.containsBlock(x, y, z);
                            boolean inside = portal.containsBlock(x, y, z);
                            if (place) {
                                if (!inBox && type == Material.NETHER_PORTAL) {
                                    block.setType(Material.AIR, false);
                                    continue;
                                }
                                if (!inBox) {
                                    continue;
                                }
                                if (!inside) {
                                    if (isPortalFill(type) || type == Material.NETHER_PORTAL) {
                                        block.setType(Material.AIR, false);
                                    }
                                    continue;
                                }
                                if (isReplaceableFill(type)) {
                                    // Stained glass (not barrier): Bedrock cannot see barriers, and
                                    // item_display portal discs are skipped by Link. Pack overlays
                                    // glass_* so YaP portal colors show on both clients.
                                    block.setType(stainedGlassFor(portal.color()), false);
                                }
                            } else if (inBox && (isPortalFill(type) || type == Material.NETHER_PORTAL)) {
                                block.setType(Material.AIR, false);
                            } else if (!inside && type == Material.NETHER_PORTAL) {
                                block.setType(Material.AIR, false);
                            }
                        }
                    }
                }
                if (place && cx == faceCx && cz == faceCz) {
                    PortalSheetDisplays.spawnFace(world, portal);
                }
            });
        }
    }

    /**
     * Keep a single disc on the portal face. Extra discs in neighboring chunks
     * are removed, and a missing face is spawned, without loading far chunks.
     */
    private void maintainDisplays(World world, Portal portal, float angle) {
        PortalCuboid box = portal.cuboid();
        int minX = box.minX() - 1;
        int maxX = box.maxX() + 1;
        int minZ = box.minZ() - 1;
        int maxZ = box.maxZ() + 1;
        double faceX = (box.minX() + box.maxX() + 1) / 2.0;
        double faceZ = (box.minZ() + box.maxZ() + 1) / 2.0;
        int faceCx = (int) Math.floor(faceX) >> 4;
        int faceCz = (int) Math.floor(faceZ) >> 4;
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add((((long) (x >> 4)) << 32) | ((z >> 4) & 0xffffffffL));
            }
        }
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            boolean face = cx == faceCx && cz == faceCz;
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                if (!world.isChunkLoaded(cx, cz)) {
                    return;
                }
                if (!face) {
                    clearDisplaysInChunk(world, cx, cz, false);
                    return;
                }
                int found = 0;
                boolean centered = false;
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                        continue;
                    }
                    found++;
                    Location at = entity.getLocation();
                    centered = Math.abs(at.getX() - faceX) < 0.6
                            && Math.abs(at.getZ() - faceZ) < 0.6;
                }
                if (found == 1 && centered) {
                    PortalSheetDisplays.spin(world, portal, angle);
                    return;
                }
                clearDisplaysInChunk(world, cx, cz, false);
                PortalSheetDisplays.spawnFace(world, portal);
            });
        }
    }

    private void clearDisplaysInChunk(World world, int cx, int cz, boolean load) {
        if (!load && !world.isChunkLoaded(cx, cz)) {
            return;
        }
        for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
            if (!e.getScoreboardTags().contains(DISPLAY_TAG)) {
                continue;
            }
            e.remove();
        }
    }

    static Material stainedGlassFor(String color) {
        String key = PortalColors.normalize(color).toUpperCase(Locale.ROOT) + "_STAINED_GLASS";
        try {
            return Material.valueOf(key);
        } catch (IllegalArgumentException e) {
            return Material.PURPLE_STAINED_GLASS;
        }
    }

    private static boolean isReplaceableFill(Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR
                || type == Material.LIGHT || type == Material.NETHER_PORTAL
                || isPortalFill(type);
    }

    private static boolean isPortalFill(Material type) {
        return type == Material.BARRIER
                || type.name().endsWith("_STAINED_GLASS")
                || type.name().endsWith("_STAINED_GLASS_PANE");
    }
}
