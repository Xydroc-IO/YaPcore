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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Colored walk-up portals using YaP resource-pack textures.
 * <p>
 * Fill: dye-colored {@code *_STAINED_GLASS} — the default pack replaces those
 * textures with animated portal sheets ({@code scripts/generate-yap-portals.py}).
 * Players trigger on standing in or adjacent to the volume (glass is solid).
 */
public final class PortalVisuals {

    private static final String DISPLAY_TAG = "yap_portal_display";

    private final JavaPlugin plugin;
    private final Map<String, List<UUID>> displays = new ConcurrentHashMap<>();

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
            YapSched.region(plugin, world, midX, midZ, () -> spawnParticles(world, box, color));
        }
    }

    private void spawnParticles(World world, PortalCuboid box, Color color) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.15f);
        int count = Math.min(40, Math.max(10, box.volumeBlocks() * 2));
        for (int i = 0; i < count; i++) {
            double x = rng.nextDouble(box.minX(), box.maxX() + 1.0);
            double y = rng.nextDouble(box.minY(), box.maxY() + 1.0);
            double z = rng.nextDouble(box.minZ(), box.maxZ() + 1.0);
            Location loc = new Location(world, x, y, z);
            world.spawnParticle(Particle.DUST, loc, 1, 0.08, 0.25, 0.08, 0.0, dust);
            if ((i & 3) == 0) {
                world.spawnParticle(Particle.PORTAL, loc, 2, 0.12, 0.35, 0.12, 0.45);
            }
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
        Material glass = stainedGlassFor(portal.color());
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                clearDisplaysInVolume(portal.name(), world, box);
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
                            boolean inside = box.containsBlock(x, y, z);
                            if (place) {
                                if (!inside && type == Material.NETHER_PORTAL) {
                                    block.setType(Material.AIR, false);
                                    continue;
                                }
                                if (!inside) {
                                    continue;
                                }
                                if (isReplaceableFill(type)) {
                                    block.setType(glass, false);
                                }
                            } else if (inside && (isPortalFill(type) || type == Material.NETHER_PORTAL)) {
                                block.setType(Material.AIR, false);
                            } else if (!inside && type == Material.NETHER_PORTAL) {
                                block.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            });
        }
    }

    private void clearDisplaysInVolume(String portalName, World world, PortalCuboid box) {
        List<UUID> ids = displays.remove(portalName);
        if (ids != null) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        Location mid = new Location(world,
                (box.minX() + box.maxX()) * 0.5 + 0.5,
                (box.minY() + box.maxY()) * 0.5 + 0.5,
                (box.minZ() + box.maxZ()) * 0.5 + 0.5);
        double rad = Math.max(box.maxX() - box.minX(), Math.max(box.maxY() - box.minY(),
                box.maxZ() - box.minZ())) + 4.0;
        for (Entity e : world.getNearbyEntities(mid, rad, rad, rad)) {
            if (e instanceof BlockDisplay && e.getScoreboardTags().contains(DISPLAY_TAG)) {
                Location l = e.getLocation();
                if (box.containsBlock(l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                    e.remove();
                }
            }
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
        return type.name().endsWith("_STAINED_GLASS")
                || type.name().endsWith("_STAINED_GLASS_PANE");
    }
}
