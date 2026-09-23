package com.yapcore.dungeons.portal;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Lime portal disc (same YaP pack art as fleet lime pads) centered in a dungeon frame.
 */
public final class DungeonPortalVisuals {

    public static final String DISPLAY_TAG = "yap_dungeon_portal_display";

    private DungeonPortalVisuals() {
    }

    public static void spawnFace(PortalStructure.Frame frame) {
        clearFace(frame);
        World world = frame.world();
        float faceW = Math.max(1f, frame.sizeAlong() - 2);
        float faceH = Math.max(1f, frame.height() - 2);
        float diameter = Math.min(faceW, faceH);
        double cx;
        double cy = frame.minY() + frame.height() / 2.0;
        double cz;
        AxisAngle4f facing;
        if (frame.axis() == Axis.X) {
            cx = (frame.minAlong() + frame.maxAlong() + 1) / 2.0;
            cz = frame.fixed() + 0.5;
            facing = new AxisAngle4f();
        } else {
            cx = frame.fixed() + 0.5;
            cz = (frame.minAlong() + frame.maxAlong() + 1) / 2.0;
            facing = new AxisAngle4f((float) (Math.PI / 2.0), 0f, 1f, 0f);
        }
        Location loc = new Location(world, cx, cy, cz);
        ItemStack visual = limeDisc();
        world.spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    facing,
                    new Vector3f(diameter, diameter, 0.35f),
                    new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(48f);
            display.setPersistent(true);
            display.addScoreboardTag(DISPLAY_TAG);
        });
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
        int minCx = (minX >> 4) - 1;
        int maxCx = (maxX >> 4) + 1;
        int minCz = (minZ >> 4) - 1;
        int maxCz = (maxZ >> 4) + 1;
        double midY = frame.minY() + frame.height() / 2.0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (var entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(entity instanceof ItemDisplay display)) {
                        continue;
                    }
                    if (!display.getScoreboardTags().contains(DISPLAY_TAG)) {
                        continue;
                    }
                    Location at = display.getLocation();
                    if (Math.abs(at.getY() - midY) > frame.height()) {
                        continue;
                    }
                    if (at.getBlockX() < minX - 2 || at.getBlockX() > maxX + 2
                            || at.getBlockZ() < minZ - 2 || at.getBlockZ() > maxZ + 2) {
                        continue;
                    }
                    display.remove();
                }
            }
        }
    }

    private static ItemStack limeDisc() {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/lime"));
        return stack;
    }
}
