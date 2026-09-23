package com.yapcore.dungeons.portal;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
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

/** One spinning lime portal sheet for dungeon openings. */
public final class DungeonPortalVisuals {

    public static final String DISPLAY_TAG = "yap_dungeon_portal_display";

    private DungeonPortalVisuals() {
    }

    public static void spawnFace(PortalStructure.Frame frame) {
        clearFace(frame);
        World world = frame.world();
        FaceGeom g = geom(frame);
        if (g == null) {
            return;
        }
        Location loc = new Location(world, g.cx, g.cy, g.cz);
        ItemStack visual = faceItem("lime");
        world.spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transform(g.facing, g.scaleX, g.scaleY, g.scaleZ, 0f));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(64f);
            display.setPersistent(true);
            display.addScoreboardTag(DISPLAY_TAG);
        });
        world.spawnParticle(org.bukkit.Particle.PORTAL, loc, 60, g.scaleX * 0.25, g.scaleY * 0.25, 0.2, 0.7);
    }

    public static void spin(PortalStructure.Frame frame, float angle) {
        FaceGeom g = geom(frame);
        if (g == null) {
            return;
        }
        World world = frame.world();
        int bx = (int) Math.floor(g.cx);
        int bz = (int) Math.floor(g.cz);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return;
        }
        Transformation next = transform(g.facing, g.scaleX, g.scaleY, g.scaleZ, angle);
        for (Entity entity : world.getChunkAt(bx >> 4, bz >> 4).getEntities()) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(DISPLAY_TAG)) {
                continue;
            }
            Location at = display.getLocation();
            if (Math.abs(at.getX() - g.cx) > 2 || Math.abs(at.getZ() - g.cz) > 2) {
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(5);
            display.setTransformation(next);
        }
    }

    public static void spinAll(float angle) {
        for (PortalStructure.Frame frame : DungeonPortalRegistry.all()) {
            spin(frame, angle);
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
        for (int cx = (minX >> 4) - 1; cx <= (maxX >> 4) + 1; cx++) {
            for (int cz = (minZ >> 4) - 1; cz <= (maxZ >> 4) + 1; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(entity instanceof ItemDisplay) && !(entity instanceof Display)) {
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

    private static FaceGeom geom(PortalStructure.Frame frame) {
        int innerMinAlong = frame.minAlong() + 1;
        int innerMaxAlong = frame.maxAlong() - 1;
        int innerMinY = frame.minY() + 1;
        int innerMaxY = frame.maxY() - 1;
        if (innerMaxAlong < innerMinAlong || innerMaxY < innerMinY) {
            return null;
        }
        float spanAlong = innerMaxAlong - innerMinAlong + 1;
        float spanY = innerMaxY - innerMinY + 1;
        double cy = (innerMinY + innerMaxY + 1) / 2.0;
        if (frame.axis() == Axis.X) {
            return new FaceGeom(
                    (innerMinAlong + innerMaxAlong + 1) / 2.0, cy, frame.fixed() + 0.5,
                    new AxisAngle4f(), spanAlong, spanY, 0.2f);
        }
        return new FaceGeom(
                frame.fixed() + 0.5, cy, (innerMinAlong + innerMaxAlong + 1) / 2.0,
                new AxisAngle4f((float) (Math.PI / 2.0), 0f, 1f, 0f),
                spanAlong, spanY, 0.2f);
    }

    private static Transformation transform(
            AxisAngle4f facing, float scaleX, float scaleY, float scaleZ, float spin) {
        return new Transformation(
                new Vector3f(), facing, new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f(spin, 0f, 0f, 1f));
    }

    private static ItemStack faceItem(String color) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/" + color));
        return stack;
    }

    private record FaceGeom(
            double cx, double cy, double cz, AxisAngle4f facing,
            float scaleX, float scaleY, float scaleZ) {
    }
}
