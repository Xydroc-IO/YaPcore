package com.yapcore.portals.enddoor;

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

/**
 * One spinning portal sheet for the whole End-door opening (fleet-pad style).
 */
public final class EndDoorVisuals {

    public static final String DISPLAY_TAG = "yap_end_door_display";

    private EndDoorVisuals() {
    }

    public static void spawnFace(EndDoorStructure.Frame frame) {
        clearFace(frame);
        World world = frame.world();
        FaceGeom g = geom(frame);
        if (g == null) {
            return;
        }
        Location loc = new Location(world, g.cx, g.cy, g.cz);
        ItemStack visual = faceItem("blue");
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
        world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, loc, 48, g.scaleX * 0.25, g.scaleY * 0.25, 0.25, 0.1);
        world.spawnParticle(org.bukkit.Particle.PORTAL, loc, 60, g.scaleX * 0.2, g.scaleY * 0.2, 0.2, 0.7);
    }

    public static void spin(EndDoorStructure.Frame frame, float angle) {
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
        int found = 0;
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
            found++;
        }
        // Chunk reload / restart often drops the ItemDisplay while the keystone remains
        if (found == 0) {
            spawnFace(frame);
            return;
        }
        if ((int) (angle * 10) % 20 == 0) {
            world.spawnParticle(org.bukkit.Particle.PORTAL,
                    new Location(world, g.cx, g.cy, g.cz), 8, g.scaleX * 0.15, g.scaleY * 0.15, 0.1, 0.35);
        }
    }

    public static void spinAll(float angle) {
        for (EndDoorStructure.Frame frame : EndDoorRegistry.all()) {
            spin(frame, angle);
        }
    }

    public static void clearFace(EndDoorStructure.Frame frame) {
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

    private static FaceGeom geom(EndDoorStructure.Frame frame) {
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
                    (innerMinAlong + innerMaxAlong + 1) / 2.0,
                    cy,
                    frame.fixed() + 0.5,
                    new AxisAngle4f(),
                    spanAlong, spanY, 0.2f);
        }
        return new FaceGeom(
                frame.fixed() + 0.5,
                cy,
                (innerMinAlong + innerMaxAlong + 1) / 2.0,
                new AxisAngle4f((float) (Math.PI / 2.0), 0f, 1f, 0f),
                spanAlong, spanY, 0.2f);
    }

    private static Transformation transform(
            AxisAngle4f facing, float scaleX, float scaleY, float scaleZ, float spin) {
        return new Transformation(
                new Vector3f(),
                facing,
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f(spin, 0f, 0f, 1f));
    }

    private static ItemStack faceItem(String color) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/" + color));
        return stack;
    }

    private record FaceGeom(
            double cx, double cy, double cz,
            AxisAngle4f facing,
            float scaleX, float scaleY, float scaleZ) {
    }
}
