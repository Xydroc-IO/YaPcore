package com.yapcore.dungeons.portal;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Lime return portal spawned after the boss dies. */
public final class DungeonExitPortal {

    public static final String DISPLAY_TAG = "yap_dungeon_exit_portal";

    private DungeonExitPortal() {
    }

    /** Builds a small glowstone pad + lime swirl at {@code origin}; returns walk AABB center. */
    public static Location spawn(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return origin;
        }
        int x = origin.getBlockX();
        int y = origin.getBlockY();
        int z = origin.getBlockZ() + 3;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.BEDROCK, false);
                world.getBlockAt(x + dx, y, z + dz).setType(Material.GLOWSTONE, false);
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, y + 3, z + dz).setType(Material.AIR, false);
            }
        }
        Location center = new Location(world, x + 0.5, y + 1.5, z + 0.5);
        ItemStack visual = new ItemStack(Material.PAPER);
        visual.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/lime"));
        world.spawn(center, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(2.2f, 3.0f, 0.25f),
                    new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setPersistent(true);
            display.addScoreboardTag(DISPLAY_TAG);
        });
        world.spawnParticle(org.bukkit.Particle.PORTAL, center, 80, 0.6, 1.0, 0.6, 0.8);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
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
}
