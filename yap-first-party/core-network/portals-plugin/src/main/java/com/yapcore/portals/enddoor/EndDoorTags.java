package com.yapcore.portals.enddoor;

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
 * Activated End doors store state on the bottom-center keystone
 * ({@link Material#END_PORTAL_FRAME} with PDC).
 */
public final class EndDoorTags {

    /** YaPDungeons structure keystone — leave those frames alone. */
    private static final NamespacedKey DUNGEON_STRUCT =
            NamespacedKey.fromString("yapdungeons:yap_dungeon_struct_portal");

    private final NamespacedKey activeKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey axisKey;
    private final NamespacedKey sizeKey;
    private final NamespacedKey heightKey;
    private final NamespacedKey minAlongKey;
    private final NamespacedKey fixedKey;
    private final NamespacedKey minYKey;

    public EndDoorTags(JavaPlugin plugin) {
        this.activeKey = new NamespacedKey(plugin, "yap_end_door");
        this.ownerKey = new NamespacedKey(plugin, "yap_end_door_owner");
        this.axisKey = new NamespacedKey(plugin, "yap_end_door_axis");
        this.sizeKey = new NamespacedKey(plugin, "yap_end_door_size");
        this.heightKey = new NamespacedKey(plugin, "yap_end_door_height");
        this.minAlongKey = new NamespacedKey(plugin, "yap_end_door_min_along");
        this.fixedKey = new NamespacedKey(plugin, "yap_end_door_fixed");
        this.minYKey = new NamespacedKey(plugin, "yap_end_door_min_y");
    }

    public NamespacedKey activeKey() {
        return activeKey;
    }

    public boolean isDungeonKeystone(Block block) {
        if (DUNGEON_STRUCT == null || !(block.getState() instanceof TileState tile)) {
            return false;
        }
        return tile.getPersistentDataContainer().has(DUNGEON_STRUCT, PersistentDataType.BYTE);
    }

    public Block installKeystone(EndDoorStructure.Frame frame, UUID owner) {
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

    public Optional<UUID> owner(Block keystone) {
        if (!(keystone.getState() instanceof TileState tile)) {
            return Optional.empty();
        }
        String raw = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<EndDoorStructure.Frame> frameFromKeystone(Block keystone) {
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
            EndDoorStructure.Frame frame = axis == Axis.X
                    ? new EndDoorStructure.Frame(keystone.getWorld(), minAlong, minY, fixed, size, height, axis)
                    : new EndDoorStructure.Frame(keystone.getWorld(), fixed, minY, minAlong, size, height, axis);
            return Optional.of(frame);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

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

    public void deactivate(Block keystone, EndDoorStructure structure) {
        Optional<EndDoorStructure.Frame> frame = frameFromKeystone(keystone);
        frame.ifPresent(f -> {
            EndDoorVisuals.clearFace(f);
            structure.clearInterior(f);
        });
        if (isKeystone(keystone)) {
            keystone.setType(structure.frameMaterial(), false);
        }
    }
}
