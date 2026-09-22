package com.yapcore.portals.service;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalColors;
import com.yapcore.portals.PortalCuboid;
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

/**
 * One swirling picture per portal, scaled to that portal's face.
 * The image grows with the blocks that were placed, so the opening reads as
 * a single pool instead of a repeated texture on every block.
 */
final class PortalSheetDisplays {

    private PortalSheetDisplays() {
    }

    static void spawnFace(World world, Portal portal) {
        PortalCuboid box = portal.cuboid();
        int spanX = box.maxX() - box.minX() + 1;
        int spanY = box.maxY() - box.minY() + 1;
        int spanZ = box.maxZ() - box.minZ() + 1;
        float scaleX;
        float scaleY;
        float scaleZ;
        AxisAngle4f rotation;
        if (spanY <= spanX && spanY <= spanZ) {
            rotation = new AxisAngle4f((float) (-Math.PI / 2.0), 1f, 0f, 0f);
            scaleX = spanX;
            scaleY = spanZ;
            scaleZ = spanY;
        } else if (spanX <= spanZ) {
            rotation = new AxisAngle4f((float) (Math.PI / 2.0), 0f, 1f, 0f);
            scaleX = spanZ;
            scaleY = spanY;
            scaleZ = spanX;
        } else {
            rotation = new AxisAngle4f();
            scaleX = spanX;
            scaleY = spanY;
            scaleZ = spanZ;
        }
        float diameter = Math.min(scaleX, scaleY);
        float faceX = diameter;
        float faceY = diameter;
        float depth = scaleZ;
        Location loc = new Location(world,
                (box.minX() + box.maxX() + 1) / 2.0,
                (box.minY() + box.maxY() + 1) / 2.0,
                (box.minZ() + box.maxZ() + 1) / 2.0);
        ItemStack visual = faceItem(portal.color());
        world.spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transform(rotation, faceX, faceY, depth, 0f));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(8f);
            display.setPersistent(true);
            display.addScoreboardTag(PortalVisuals.DISPLAY_TAG);
        });
    }

    /** Turn the disc in place. One revolution is a full spin of the same picture. */
    static void spin(World world, Portal portal, float angle) {
        PortalCuboid box = portal.cuboid();
        int bx = (int) Math.floor((box.minX() + box.maxX() + 1) / 2.0);
        int bz = (int) Math.floor((box.minZ() + box.maxZ() + 1) / 2.0);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return;
        }
        int spanX = box.maxX() - box.minX() + 1;
        int spanY = box.maxY() - box.minY() + 1;
        int spanZ = box.maxZ() - box.minZ() + 1;
        float scaleX;
        float scaleY;
        float scaleZ;
        AxisAngle4f rotation;
        if (spanY <= spanX && spanY <= spanZ) {
            rotation = new AxisAngle4f((float) (-Math.PI / 2.0), 1f, 0f, 0f);
            scaleX = spanX;
            scaleY = spanZ;
            scaleZ = spanY;
        } else if (spanX <= spanZ) {
            rotation = new AxisAngle4f((float) (Math.PI / 2.0), 0f, 1f, 0f);
            scaleX = spanZ;
            scaleY = spanY;
            scaleZ = spanX;
        } else {
            rotation = new AxisAngle4f();
            scaleX = spanX;
            scaleY = spanY;
            scaleZ = spanZ;
        }
        float diameter = Math.min(scaleX, scaleY);
        Transformation next = transform(rotation, diameter, diameter, scaleZ, angle);
        for (org.bukkit.entity.Entity entity : world.getChunkAt(bx >> 4, bz >> 4).getEntities()) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(PortalVisuals.DISPLAY_TAG)) {
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(5);
            display.setTransformation(next);
        }
    }

    private static Transformation transform(
            AxisAngle4f facing, float scaleX, float scaleY, float scaleZ, float spin) {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                facing,
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f(spin, 0f, 0f, 1f));
    }

    private static ItemStack faceItem(String colorName) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("yap", "portal/" + PortalColors.normalize(colorName)));
        return stack;
    }
}
