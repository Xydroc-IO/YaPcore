package com.yapcore.world.edit;

import com.sk89q.worldedit.extent.EditSession;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Folia-safe flush for WorldEdit EditSession queues. */
public final class WorldEditBridge implements EditSession.YaPEditBridge {

    private final BlockBatch batch;

    public WorldEditBridge(JavaPlugin plugin, UndoService undo) {
        this.batch = new BlockBatch(plugin, undo);
    }

    public void setEditState(PlayerEditState state) {
        batch.setEditState(state);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    @Override
    public CompletableFuture<Integer> apply(Player player, World world, List<EditSession.Queued> blocks) {
        List<BlockBatch.Planned> plans = new ArrayList<>(blocks.size());
        for (EditSession.Queued q : blocks) {
            try {
                BlockData data = org.bukkit.Bukkit.createBlockData(q.data());
                plans.add(new BlockBatch.Planned(q.x(), q.y(), q.z(), data.getMaterial(), data));
            } catch (IllegalArgumentException e) {
                plans.add(new BlockBatch.Planned(q.x(), q.y(), q.z(),
                        org.bukkit.Material.matchMaterial(q.data()) == null
                                ? org.bukkit.Material.STONE
                                : org.bukkit.Material.matchMaterial(q.data())));
            }
        }
        return batch.apply(player, world, plans);
    }
}
