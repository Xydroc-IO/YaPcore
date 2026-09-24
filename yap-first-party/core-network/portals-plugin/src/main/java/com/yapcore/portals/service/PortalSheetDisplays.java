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

import java.util.Locale;

/**
 * One swirling picture per portal, scaled to that portal's face.
 * The image grows with the blocks that were placed, so the opening reads as
 * a single pool instead of a repeated texture on every block.
 */
final class PortalSheetDisplays {

    private PortalSheetDisplays() {
    }

    /** Per-portal tag so neighboring pads do not steal / respawn each other's disc. */
    static String portalTag(Portal portal) {
        String id = portal == null || portal.name() == null ? "unknown" : portal.name();
        String clean = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        if (clean.length() > 40) {
            clean = clean.substring(0, 40);
        }
        return "yap_portal_id_" + clean;
    }

    static void spawnFace(World world, Portal portal) {
        clearFace(world, portal);
        PortalCuboid box = portal.cuboid();
        FaceGeom g = geom(box);
        Location loc = new Location(world, g.cx, g.cy, g.cz);
        ItemStack visual = faceItem(portal.color());
        String idTag = portalTag(portal);
        world.spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transform(g.facing, g.faceX, g.faceY, g.depth, 0f));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(8f);
            display.setPersistent(true);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.addScoreboardTag(PortalVisuals.DISPLAY_TAG);
            display.addScoreboardTag(idTag);
        });
    }

    /** Drop this portal's disc and any untagged leftovers sitting on the same face. */
    static void clearFace(World world, Portal portal) {
        PortalCuboid box = portal.cuboid();
        FaceGeom g = geom(box);
        int minCx = (box.minX() - 1) >> 4;
        int maxCx = (box.maxX() + 1) >> 4;
        int minCz = (box.minZ() - 1) >> 4;
        int maxCz = (box.maxZ() + 1) >> 4;
        String idTag = portalTag(portal);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (org.bukkit.entity.Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!entity.getScoreboardTags().contains(PortalVisuals.DISPLAY_TAG)) {
                        continue;
                    }
                    boolean ours = entity.getScoreboardTags().contains(idTag);
                    boolean orphan = !hasPortalIdTag(entity);
                    Location at = entity.getLocation();
                    boolean near = Math.abs(at.getX() - g.cx) < 2.5
                            && Math.abs(at.getY() - g.cy) < 3.5
                            && Math.abs(at.getZ() - g.cz) < 2.5;
                    if (ours || (near && orphan)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    static boolean hasPortalIdTag(org.bukkit.entity.Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag != null && tag.startsWith("yap_portal_id_")) {
                return true;
            }
        }
        return false;
    }

    /** Turn the disc in place. One revolution is a full spin of the same picture. */
    static void spin(World world, Portal portal, float angle) {
        PortalCuboid box = portal.cuboid();
        FaceGeom g = geom(box);
        int bx = (int) Math.floor(g.cx);
        int bz = (int) Math.floor(g.cz);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return;
        }
        Transformation next = transform(g.facing, g.faceX, g.faceY, g.depth, angle);
        String idTag = portalTag(portal);
        for (org.bukkit.entity.Entity entity : world.getChunkAt(bx >> 4, bz >> 4).getEntities()) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(PortalVisuals.DISPLAY_TAG)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(idTag)) {
                continue;
            }
            // Keep interpolating between ticks so the disc turns instead of popping.
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(6);
            display.setTransformation(next);
        }
    }

    static boolean isThisPortal(org.bukkit.entity.Entity entity, Portal portal) {
        return entity.getScoreboardTags().contains(PortalVisuals.DISPLAY_TAG)
                && entity.getScoreboardTags().contains(portalTag(portal));
    }

    private static FaceGeom geom(PortalCuboid box) {
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
        return new FaceGeom(
                (box.minX() + box.maxX() + 1) / 2.0,
                (box.minY() + box.maxY() + 1) / 2.0,
                (box.minZ() + box.maxZ() + 1) / 2.0,
                rotation,
                diameter,
                diameter,
                scaleZ);
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

    private record FaceGeom(
            double cx, double cy, double cz,
            AxisAngle4f facing,
            float faceX, float faceY, float depth) {
    }
}
