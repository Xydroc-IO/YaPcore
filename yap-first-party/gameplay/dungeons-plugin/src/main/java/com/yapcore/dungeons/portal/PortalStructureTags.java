package com.yapcore.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Activated buildable portals store state on the bottom-center keystone
 * ({@link Material#END_PORTAL_FRAME} with PDC).
 */
public final class PortalStructureTags {

    private final NamespacedKey activeKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey axisKey;
    private final NamespacedKey sizeKey;
    private final NamespacedKey heightKey;
    private final NamespacedKey minAlongKey;
    private final NamespacedKey fixedKey;
    private final NamespacedKey minYKey;

    public PortalStructureTags(JavaPlugin plugin) {
        this.activeKey = new NamespacedKey(plugin, "yap_dungeon_struct_portal");
        this.ownerKey = new NamespacedKey(plugin, "yap_dungeon_struct_owner");
        this.axisKey = new NamespacedKey(plugin, "yap_dungeon_struct_axis");
        this.sizeKey = new NamespacedKey(plugin, "yap_dungeon_struct_size");
        this.heightKey = new NamespacedKey(plugin, "yap_dungeon_struct_height");
        this.minAlongKey = new NamespacedKey(plugin, "yap_dungeon_struct_min_along");
        this.fixedKey = new NamespacedKey(plugin, "yap_dungeon_struct_fixed");
        this.minYKey = new NamespacedKey(plugin, "yap_dungeon_struct_min_y");
    }

    public NamespacedKey activeKey() {
        return activeKey;
    }

    public NamespacedKey ownerKey() {
        return ownerKey;
    }

    /** Turn the bottom-center frame block into a tagged keystone and return it. */
    public Block installKeystone(PortalStructure.Frame frame, UUID owner) {
        Block key = frame.keystone();
        key.setType(Material.END_PORTAL_FRAME, false);
        if (key.getState() instanceof TileState tile) {
            var pdc = tile.getPersistentDataContainer();
            pdc.set(activeKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
            pdc.set(axisKey, PersistentDataType.STRING, frame.axis().name());
            pdc.set(sizeKey, PersistentDataType.INTEGER, frame.sizeAlong());
            pdc.set(heightKey, PersistentDataType.INTEGER, frame.height());
            pdc.set(minAlongKey, PersistentDataType.INTEGER, frame.minAlong());
            pdc.set(fixedKey, PersistentDataType.INTEGER, frame.fixed());
            pdc.set(minYKey, PersistentDataType.INTEGER, frame.minY());
            tile.update();
        }
        return key;
    }

    public boolean isKeystone(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return false;
        }
        return tile.getPersistentDataContainer().has(activeKey, PersistentDataType.BYTE);
    }

    public Optional<String> owner(Block keystone) {
        if (!(keystone.getState() instanceof TileState tile)) {
            return Optional.empty();
        }
        String raw = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return raw == null ? Optional.empty() : Optional.of(raw);
    }

    public Optional<PortalStructure.Frame> frameFromKeystone(Block keystone) {
        if (!(keystone.getState() instanceof TileState tile)) {
            return Optional.empty();
        }
        var pdc = tile.getPersistentDataContainer();
        if (!pdc.has(activeKey, PersistentDataType.BYTE)) {
            return Optional.empty();
        }
        try {
            Axis axis = Axis.valueOf(pdc.get(axisKey, PersistentDataType.STRING));
            int size = pdc.getOrDefault(sizeKey, PersistentDataType.INTEGER, 4);
            int height = pdc.getOrDefault(heightKey, PersistentDataType.INTEGER, 5);
            int minAlong = pdc.getOrDefault(minAlongKey, PersistentDataType.INTEGER, 0);
            int fixed = pdc.getOrDefault(fixedKey, PersistentDataType.INTEGER, 0);
            int minY = pdc.getOrDefault(minYKey, PersistentDataType.INTEGER, keystone.getY());
            PortalStructure.Frame frame = axis == Axis.X
                    ? new PortalStructure.Frame(keystone.getWorld(), minAlong, minY, fixed, size, height, axis)
                    : new PortalStructure.Frame(keystone.getWorld(), fixed, minY, minAlong, size, height, axis);
            return Optional.of(frame);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Find an activated keystone near a clicked block (frame or interior). */
    public Optional<Block> findNearbyKeystone(Block clicked, int radius) {
        if (isKeystone(clicked)) {
            return Optional.of(clicked);
        }
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = clicked.getRelative(dx, dy, dz);
                    if (isKeystone(b)) {
                        return Optional.of(b);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public void deactivate(Block keystone, PortalStructure structure) {
        Optional<PortalStructure.Frame> frame = frameFromKeystone(keystone);
        frame.ifPresent(structure::clearInterior);
        if (isKeystone(keystone)) {
            keystone.setType(structure.frameMaterial(), false);
        }
    }
}
