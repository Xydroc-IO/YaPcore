package com.yapcore.dungeons.portal;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lime return portal after boss clear — same spinning sheet as entrance portals.
 * No solid glowstone pad (that used to block walking into the swirl).
 */
public final class DungeonExitPortal {

    public static final String DISPLAY_TAG = "yap_dungeon_exit_portal";

    /** runId → display entity UUID for the spin timer. */
    private static final Map<String, UUID> ACTIVE = new ConcurrentHashMap<>();

    private DungeonExitPortal() {
    }

    /**
     * Clears a walk column, places a thin floor light underfoot, spawns a spinning
     * lime sheet. Returns the feet location players walk into.
     */
    public static Location spawn(Location origin, String runId) {
        World world = origin.getWorld();
        if (world == null) {
            return origin;
        }
        int x = origin.getBlockX();
        // bossArena is feet at originY+1; keep floor underfoot, portal volume air
        int floorY = origin.getBlockY() - 1;
        int z = origin.getBlockZ() + 3;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, floorY - 1, z + dz).setType(Material.BEDROCK, false);
                // Lit floor only — never solid blocks at walk height
                world.getBlockAt(x + dx, floorY, z + dz).setType(Material.SEA_LANTERN, false);
                for (int dy = 1; dy <= 4; dy++) {
                    world.getBlockAt(x + dx, floorY + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }

        Location center = new Location(world, x + 0.5, floorY + 2.0, z + 0.5);
        world.getChunkAt(center);
        removeExisting(world, center);

        ItemStack visual = new ItemStack(Material.PAPER);
        visual.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/lime"));
        ItemDisplay display = world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(0f));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBillboard(Display.Billboard.FIXED);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(128f);
            d.setPersistent(true);
            d.addScoreboardTag(DISPLAY_TAG);
            if (runId != null && !runId.isBlank()) {
                d.addScoreboardTag("yap_dungeon_exit:" + runId);
            }
        });
        if (runId != null && !runId.isBlank()) {
            ACTIVE.put(runId, display.getUniqueId());
        }
        world.spawnParticle(org.bukkit.Particle.PORTAL, center, 60, 0.5, 1.0, 0.5, 0.6);
        // Feet on the lantern floor, centered under the sheet
        return new Location(world, x + 0.5, floorY + 1.0, z + 0.5);
    }

    /** @deprecated use {@link #spawn(Location, String)} */
    @Deprecated
    public static Location spawn(Location origin) {
        return spawn(origin, null);
    }

    public static void spinAll(float angle) {
        Transformation next = transform(angle);
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, UUID> e : ACTIVE.entrySet()) {
            // Resolve via any world that has the entity (dungeon run worlds)
            ItemDisplay display = findDisplay(e.getValue());
            if (display == null || display.isDead()) {
                dead.add(e.getKey());
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(next);
        }
        for (String id : dead) {
            ACTIVE.remove(id);
        }
    }

    public static void clear(String runId) {
        if (runId == null) {
            return;
        }
        UUID id = ACTIVE.remove(runId);
        if (id == null) {
            return;
        }
        ItemDisplay display = findDisplay(id);
        if (display != null) {
            display.remove();
        }
    }

    public static boolean near(Location player, Location exit, double radius) {
        if (player == null || exit == null || player.getWorld() == null || exit.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(exit.getWorld())) {
            return false;
        }
        return player.distanceSquared(exit) <= radius * radius;
    }

    private static void removeExisting(World world, Location center) {
        for (Entity e : world.getNearbyEntities(center, 3, 4, 3)) {
            if (e instanceof ItemDisplay d && d.getScoreboardTags().contains(DISPLAY_TAG)) {
                d.remove();
            }
        }
    }

    private static ItemDisplay findDisplay(UUID id) {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            Entity e = world.getEntity(id);
            if (e instanceof ItemDisplay display) {
                return display;
            }
        }
        return null;
    }

    private static Transformation transform(float spin) {
        return new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(2.2f, 3.0f, 0.25f),
                new AxisAngle4f(spin, 0f, 0f, 1f));
    }
}
