package com.yapcore.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Animated lime portal face for dungeon frames.
 * {@link BlockDisplay} of lime stained glass uses the pack flipbook with no collision,
 * so the opening stays walkable while the swirl still renders.
 */
public final class DungeonPortalVisuals {

    public static final String DISPLAY_TAG = "yap_dungeon_portal_display";

    private DungeonPortalVisuals() {
    }

    public static void spawnFace(PortalStructure.Frame frame) {
        clearFace(frame);
        World world = frame.world();
        for (Block cell : frame.interiorBlocks()) {
            Location loc = cell.getLocation();
            final Vector3f translation;
            final Vector3f scale;
            if (frame.axis() == Axis.X) {
                translation = new Vector3f(0f, 0f, 0.35f);
                scale = new Vector3f(1.02f, 1.02f, 0.3f);
            } else {
                translation = new Vector3f(0.35f, 0f, 0f);
                scale = new Vector3f(0.3f, 1.02f, 1.02f);
            }
            world.spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
                display.setTransformation(new Transformation(
                        translation,
                        new AxisAngle4f(),
                        scale,
                        new AxisAngle4f()));
                display.setBrightness(new Display.Brightness(15, 15));
                display.setBillboard(Display.Billboard.FIXED);
                display.setShadowRadius(0f);
                display.setShadowStrength(0f);
                display.setViewRange(64f);
                display.setPersistent(true);
                display.addScoreboardTag(DISPLAY_TAG);
            });
        }
    }

    public static void clearFace(PortalStructure.Frame frame) {
        World world = frame.world();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        if (frame.axis() == Axis.X) {
            minX = frame.minAlong();
            maxX = frame.maxAlong();
            minZ = frame.fixed();
            maxZ = frame.fixed();
        } else {
            minX = frame.fixed();
            maxX = frame.fixed();
            minZ = frame.minAlong();
            maxZ = frame.maxAlong();
        }
        int minY = frame.minY();
        int maxY = frame.maxY();
        int minCx = (minX >> 4) - 1;
        int maxCx = (maxX >> 4) + 1;
        int minCz = (minZ >> 4) - 1;
        int maxCz = (maxZ >> 4) + 1;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(entity instanceof BlockDisplay) && !(entity instanceof ItemDisplay)) {
                        continue;
                    }
                    if (!entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                        continue;
                    }
                    Location at = entity.getLocation();
                    if (at.getBlockY() < minY - 1 || at.getBlockY() > maxY + 1) {
                        continue;
                    }
                    if (at.getBlockX() < minX - 1 || at.getBlockX() > maxX + 1
                            || at.getBlockZ() < minZ - 1 || at.getBlockZ() > maxZ + 1) {
                        continue;
                    }
                    entity.remove();
                }
            }
        }
    }
}
