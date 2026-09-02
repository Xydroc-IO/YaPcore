package com.sk89q.worldedit.extent;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.yapcore.world.EditApplyService;
import com.yapcore.world.WorldServices;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EditSession stand-in: queues setBlock calls and flushes through YaPWorld when closed.
 */
public class EditSession implements AutoCloseable {

    private final World world;
    private final int maxBlocks;
    private final List<Queued> queue = new ArrayList<>();
    private UUID actor;
    private boolean closed;

    public EditSession(World world, int maxBlocks) {
        this.world = world;
        this.maxBlocks = maxBlocks < 0 ? Integer.MAX_VALUE : maxBlocks;
    }

    public void setActor(UUID playerId) {
        this.actor = playerId;
    }

    public World getWorld() {
        return world;
    }

    public boolean setBlock(BlockVector3 position, BlockState block) {
        if (closed || position == null || block == null) {
            return false;
        }
        if (queue.size() >= maxBlocks) {
            return false;
        }
        queue.add(new Queued(position.x(), position.y(), position.z(), block.getBlockData().getAsString()));
        return true;
    }

    public BlockState getBlock(BlockVector3 position) {
        org.bukkit.World bw = BukkitAdapter.adapt(world);
        if (bw == null) {
            return BlockState.of(Material.AIR.createBlockData());
        }
        return BlockState.of(bw.getBlockAt(position.x(), position.y(), position.z()).getBlockData());
    }

    public int size() {
        return queue.size();
    }

    /** Flush queued changes via YaPWorld when a player actor is known. */
    public CompletableFuture<Integer> flushAsync() {
        if (queue.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        org.bukkit.World bw = world instanceof BukkitWorld bworld ? bworld.getWorld() : BukkitAdapter.adapt(world);
        if (bw == null) {
            return CompletableFuture.completedFuture(0);
        }
        Player player = actor == null ? null : Bukkit.getPlayer(actor);
        if (player == null) {
            // Best-effort sync apply without undo
            int n = 0;
            for (Queued q : queue) {
                try {
                    bw.getBlockAt(q.x, q.y, q.z).setBlockData(Bukkit.createBlockData(q.data), false);
                    n++;
                } catch (IllegalArgumentException ignored) {
                }
            }
            queue.clear();
            return CompletableFuture.completedFuture(n);
        }
        // Apply via EditApplyService one pattern at a time is inefficient — direct set via fill
        // Use replace-style: build a temporary approach through EditApplyService.fillPattern per unique
        EditApplyService edit = WorldServices.editApply().orElse(null);
        if (edit == null) {
            int n = 0;
            for (Queued q : queue) {
                try {
                    bw.getBlockAt(q.x, q.y, q.z).setBlockData(Bukkit.createBlockData(q.data), false);
                    n++;
                } catch (IllegalArgumentException ignored) {
                }
            }
            queue.clear();
            return CompletableFuture.completedFuture(n);
        }
        // Delegate to YaPWorld-native flush hook if registered
        YaPEditBridge bridge = YaPEditBridge.get();
        if (bridge != null) {
            List<Queued> copy = new ArrayList<>(queue);
            queue.clear();
            return bridge.apply(player, bw, copy);
        }
        int n = 0;
        for (Queued q : queue) {
            try {
                bw.getBlockAt(q.x, q.y, q.z).setBlockData(Bukkit.createBlockData(q.data), false);
                n++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        queue.clear();
        return CompletableFuture.completedFuture(n);
    }

    @Override
    public void close() {
        if (!closed) {
            flushAsync().join();
            closed = true;
        }
    }

    public record Queued(int x, int y, int z, String data) {
    }

    /** Set by YaPWorld to Folia-safe apply. */
    public interface YaPEditBridge {
        CompletableFuture<Integer> apply(Player player, org.bukkit.World world, List<Queued> blocks);

        static YaPEditBridge get() {
            return Holder.BRIDGE;
        }

        static void set(YaPEditBridge bridge) {
            Holder.BRIDGE = bridge;
        }

        final class Holder {
            private static YaPEditBridge BRIDGE;
        }
    }
}
