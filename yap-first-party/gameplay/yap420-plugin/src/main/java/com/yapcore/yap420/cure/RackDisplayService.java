package com.yapcore.yap420.cure;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Drying rack visuals via BlockDisplay (oak planks) — no resource-pack dependency.
 * Barrier at the block for a solid hitbox / break target.
 */
public final class RackDisplayService {

    private final JavaPlugin plugin;
    private final Yap420Keys keys;
    private final BlockData wood;

    public RackDisplayService(JavaPlugin plugin, Yap420Keys keys, com.yapcore.yap420.item.ItemBridge items) {
        this.plugin = plugin;
        this.keys = keys;
        this.wood = Material.OAK_PLANKS.createBlockData();
        // items unused for visuals; kept for constructor compat with Yap420Plugin
    }

    public RackState spawn(RackState rack) {
        World world = Bukkit.getWorld(rack.world());
        if (world == null) {
            return rack;
        }
        clearDisplays(rack);
        Block block = world.getBlockAt(rack.x(), rack.y(), rack.z());
        if (block.getType().isAir() || block.getType() == Material.BARRIER) {
            block.setType(Material.BARRIER, false);
        }
        Location base = new Location(world, rack.x() + 0.5, rack.y(), rack.z() + 0.5);
        List<UUID> parts = new ArrayList<>();
        // two posts + three rungs (local units in blocks)
        parts.add(spawnPart(base, -0.35f, 0f, 0f, 0.12f, 1.0f, 0.12f, rack));
        parts.add(spawnPart(base, 0.35f, 0f, 0f, 0.12f, 1.0f, 0.12f, rack));
        parts.add(spawnPart(base, 0f, 0.25f, 0f, 0.75f, 0.08f, 0.08f, rack));
        parts.add(spawnPart(base, 0f, 0.55f, 0f, 0.75f, 0.08f, 0.08f, rack));
        parts.add(spawnPart(base, 0f, 0.85f, 0f, 0.75f, 0.08f, 0.08f, rack));
        // store primary entity as first post for resolve/remove
        UUID primary = parts.isEmpty() ? null : parts.getFirst();
        return rack.withEntity(primary);
    }

    private UUID spawnPart(Location base, float ox, float oy, float oz, float sx, float sy, float sz, RackState rack) {
        Location loc = base.clone().add(ox, oy, oz);
        BlockDisplay display = base.getWorld().spawn(loc, BlockDisplay.class, ent -> {
            ent.setBlock(wood);
            ent.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(sx, sy, sz),
                    new AxisAngle4f()));
            ent.setPersistent(true);
            ent.getPersistentDataContainer().set(keys.rackId(), PersistentDataType.STRING, rack.key());
        });
        return display.getUniqueId();
    }

    public void remove(RackState rack) {
        clearDisplays(rack);
        World world = Bukkit.getWorld(rack.world());
        if (world != null) {
            Block block = world.getBlockAt(rack.x(), rack.y(), rack.z());
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private void clearDisplays(RackState rack) {
        World world = Bukkit.getWorld(rack.world());
        if (world == null) {
            return;
        }
        String key = rack.key();
        // Remove any tagged displays near the rack (posts/rungs)
        Location center = new Location(world, rack.x() + 0.5, rack.y() + 0.5, rack.z() + 0.5);
        for (Entity ent : world.getNearbyEntities(center, 1.5, 1.5, 1.5)) {
            if (ent instanceof BlockDisplay || ent instanceof org.bukkit.entity.ItemDisplay) {
                String id = ent.getPersistentDataContainer().get(keys.rackId(), PersistentDataType.STRING);
                if (key.equals(id)) {
                    ent.remove();
                }
            }
        }
        UUID id = rack.entityUuid();
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public RackState ensureDisplay(RackState rack) {
        World world = Bukkit.getWorld(rack.world());
        if (world == null) {
            return rack;
        }
        Location center = new Location(world, rack.x() + 0.5, rack.y() + 0.5, rack.z() + 0.5);
        boolean hasPart = false;
        for (Entity ent : world.getNearbyEntities(center, 1.2, 1.2, 1.2)) {
            if (ent instanceof BlockDisplay) {
                String id = ent.getPersistentDataContainer().get(keys.rackId(), PersistentDataType.STRING);
                if (rack.key().equals(id)) {
                    hasPart = true;
                    break;
                }
            }
        }
        if (hasPart) {
            Block block = world.getBlockAt(rack.x(), rack.y(), rack.z());
            if (block.getType().isAir()) {
                block.setType(Material.BARRIER, false);
            }
            return rack;
        }
        return spawn(rack);
    }

    public void respawnAll(RackRegistry registry) {
        for (RackState rack : registry.all()) {
            World world = Bukkit.getWorld(rack.world());
            if (world == null) {
                continue;
            }
            YapSched.region(plugin, world, rack.x(), rack.z(), () -> {
                RackState updated = spawn(rack);
                registry.put(updated);
            });
        }
    }

    public void safeSpawn(RackState rack, RackRegistry registry) {
        try {
            registry.put(spawn(rack));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Rack spawn failed " + rack.key(), e);
        }
    }
}
