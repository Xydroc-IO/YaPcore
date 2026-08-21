package com.yapcore.stacker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Locale;

/** Creature spawner stacking via TileState PDC. */
public final class SpawnerStackService {

    private final StackerConfig config;
    private final StackKeys keys;
    private final StackerMetrics metrics;

    public SpawnerStackService(StackerConfig config, StackKeys keys, StackerMetrics metrics) {
        this.config = config;
        this.keys = keys;
        this.metrics = metrics;
    }

    public boolean enabled() {
        return config.enabled() && config.spawnersEnabled();
    }

    public int getStack(CreatureSpawner spawner) {
        Integer v = spawner.getPersistentDataContainer().get(keys.spawnerStack, StackKeys.INT);
        return v == null ? 1 : Math.max(1, v);
    }

    public void setStack(CreatureSpawner spawner, int size) {
        int capped = Math.min(Math.max(1, size), config.spawnerMaxStack());
        if (capped <= 1) {
            spawner.getPersistentDataContainer().remove(keys.spawnerStack);
        } else {
            spawner.getPersistentDataContainer().set(keys.spawnerStack, StackKeys.INT, capped);
        }
        spawner.update(true, false);
    }

    public EntityType spawnType(CreatureSpawner spawner) {
        EntityType t = spawner.getSpawnedType();
        return t == null ? EntityType.PIG : t;
    }

    public CreatureSpawner asSpawner(Block block) {
        if (block.getType() != Material.SPAWNER) {
            return null;
        }
        if (block.getState() instanceof CreatureSpawner cs) {
            return cs;
        }
        return null;
    }

    /** Find nearby same-type spawner with room. */
    public CreatureSpawner findHost(Block placedOrSource, EntityType type) {
        if (!enabled() || !config.worldEnabled(placedOrSource.getWorld().getName())) {
            return null;
        }
        Location origin = placedOrSource.getLocation().add(0.5, 0.5, 0.5);
        double r = config.spawnerMergeRadius();
        int rBlocks = (int) Math.ceil(r);
        CreatureSpawner best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -rBlocks; x <= rBlocks; x++) {
            for (int y = -rBlocks; y <= rBlocks; y++) {
                for (int z = -rBlocks; z <= rBlocks; z++) {
                    Block b = placedOrSource.getRelative(x, y, z);
                    if (b.equals(placedOrSource)) {
                        continue;
                    }
                    CreatureSpawner cs = asSpawner(b);
                    if (cs == null) {
                        continue;
                    }
                    if (spawnType(cs) != type) {
                        continue;
                    }
                    if (getStack(cs) >= config.spawnerMaxStack()) {
                        continue;
                    }
                    double d = b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin);
                    if (d <= r * r && d < bestDist) {
                        bestDist = d;
                        best = cs;
                    }
                }
            }
        }
        return best;
    }

    public boolean tryAbsorbIntoNearby(Block justPlaced) {
        CreatureSpawner self = asSpawner(justPlaced);
        if (self == null) {
            return false;
        }
        EntityType type = spawnType(self);
        CreatureSpawner host = findHost(justPlaced, type);
        if (host == null) {
            return false;
        }
        int combined = getStack(host) + getStack(self);
        setStack(host, combined);
        justPlaced.setType(Material.AIR);
        metrics.spawnerStack();
        return true;
    }

    public int absorbNearbyInto(Block hostBlock) {
        CreatureSpawner host = asSpawner(hostBlock);
        if (host == null) {
            return 0;
        }
        EntityType type = spawnType(host);
        Location origin = hostBlock.getLocation().add(0.5, 0.5, 0.5);
        double r = config.spawnerMergeRadius();
        int rBlocks = (int) Math.ceil(r);
        int absorbed = 0;
        for (int x = -rBlocks; x <= rBlocks; x++) {
            for (int y = -rBlocks; y <= rBlocks; y++) {
                for (int z = -rBlocks; z <= rBlocks; z++) {
                    Block b = hostBlock.getRelative(x, y, z);
                    if (b.equals(hostBlock)) {
                        continue;
                    }
                    CreatureSpawner cs = asSpawner(b);
                    if (cs == null || spawnType(cs) != type) {
                        continue;
                    }
                    if (b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin) > r * r) {
                        continue;
                    }
                    int room = config.spawnerMaxStack() - getStack(host);
                    if (room <= 0) {
                        return absorbed;
                    }
                    int take = Math.min(room, getStack(cs));
                    setStack(host, getStack(host) + take);
                    int left = getStack(cs) - take;
                    if (left <= 0) {
                        b.setType(Material.AIR);
                    } else {
                        setStack(cs, left);
                    }
                    absorbed += take;
                    metrics.spawnerStack();
                }
            }
        }
        return absorbed;
    }

    public ItemStack createSpawnerItem(EntityType type, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.min(64, Math.max(1, amount)));
        item.editMeta(meta -> {
            if (meta instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(type);
                    bsm.setBlockState(cs);
                }
            }
            meta.displayName(net.kyori.adventure.text.Component.text(
                    type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ')
                            + " Spawner"));
        });
        return item;
    }
}
