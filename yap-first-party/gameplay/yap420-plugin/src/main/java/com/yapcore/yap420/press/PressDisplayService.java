package com.yapcore.yap420.press;

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

import java.util.UUID;
import java.util.logging.Level;

/** Packaging press visuals — iron base + piston head (no pack art required). */
public final class PressDisplayService {

    private final JavaPlugin plugin;
    private final Yap420Keys keys;
    private final BlockData iron;
    private final BlockData piston;

    public PressDisplayService(JavaPlugin plugin, Yap420Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
        this.iron = Material.IRON_BLOCK.createBlockData();
        this.piston = Material.PISTON.createBlockData();
    }

    public PressState spawn(PressState press) {
        World world = Bukkit.getWorld(press.world());
        if (world == null) {
            return press;
        }
        clearDisplays(press);
        Block block = world.getBlockAt(press.x(), press.y(), press.z());
        if (block.getType().isAir() || block.getType() == Material.BARRIER) {
            block.setType(Material.BARRIER, false);
        }
        Location base = new Location(world, press.x() + 0.5, press.y(), press.z() + 0.5);
        // base plate
        spawnPart(base, 0f, 0.05f, 0f, 0.9f, 0.15f, 0.9f, iron, press);
        // side posts
        spawnPart(base, -0.35f, 0.2f, -0.35f, 0.12f, 0.7f, 0.12f, iron, press);
        spawnPart(base, 0.35f, 0.2f, -0.35f, 0.12f, 0.7f, 0.12f, iron, press);
        spawnPart(base, -0.35f, 0.2f, 0.35f, 0.12f, 0.7f, 0.12f, iron, press);
        spawnPart(base, 0.35f, 0.2f, 0.35f, 0.12f, 0.7f, 0.12f, iron, press);
        // press plate (piston head look)
        UUID primary = spawnPart(base, 0f, 0.75f, 0f, 0.7f, 0.2f, 0.7f, piston, press);
        return press.withEntity(primary);
    }

    private UUID spawnPart(Location base, float ox, float oy, float oz,
                           float sx, float sy, float sz, BlockData data, PressState press) {
        Location loc = base.clone().add(ox, oy, oz);
        BlockDisplay display = base.getWorld().spawn(loc, BlockDisplay.class, ent -> {
            ent.setBlock(data);
            ent.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(sx, sy, sz),
                    new AxisAngle4f()));
            ent.setPersistent(true);
            ent.getPersistentDataContainer().set(keys.pressId(), PersistentDataType.STRING, press.key());
        });
        return display.getUniqueId();
    }

    public void remove(PressState press) {
        clearDisplays(press);
        World world = Bukkit.getWorld(press.world());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(press.x(), press.y(), press.z());
        if (block.getType() == Material.BARRIER) {
            block.setType(Material.AIR, false);
        }
    }

    public PressState ensureDisplay(PressState press) {
        World world = Bukkit.getWorld(press.world());
        if (world == null) {
            return press;
        }
        if (press.entityUuid() != null) {
            Entity ent = Bukkit.getEntity(press.entityUuid());
            if (ent instanceof BlockDisplay) {
                Block block = world.getBlockAt(press.x(), press.y(), press.z());
                if (block.getType().isAir()) {
                    block.setType(Material.BARRIER, false);
                }
                return press;
            }
        }
        return spawn(press);
    }

    public void respawnAll(PressRegistry registry) {
        for (PressState press : registry.all()) {
            registry.put(spawn(press));
        }
    }

    private void clearDisplays(PressState press) {
        World world = Bukkit.getWorld(press.world());
        if (world == null) {
            return;
        }
        Location center = new Location(world, press.x() + 0.5, press.y() + 0.5, press.z() + 0.5);
        try {
            for (Entity ent : world.getNearbyEntities(center, 1.5, 1.5, 1.5)) {
                if (!(ent instanceof BlockDisplay)) {
                    continue;
                }
                String id = ent.getPersistentDataContainer().get(keys.pressId(), PersistentDataType.STRING);
                if (press.key().equals(id)) {
                    ent.remove();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "clear press displays", e);
        }
        if (press.entityUuid() != null) {
            Entity ent = Bukkit.getEntity(press.entityUuid());
            if (ent != null) {
                ent.remove();
            }
        }
    }

    public void removeLater(PressState press) {
        YapSched.region(plugin, new Location(
                Bukkit.getWorld(press.world()), press.x(), press.y(), press.z()), () -> remove(press));
    }
}
