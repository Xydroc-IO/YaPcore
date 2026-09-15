package com.yapcore.protect.service;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.model.ProtectChange;
import com.yapcore.protect.util.BlockCodec;
import com.yapcore.protect.util.InventoryCodec;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

/** In-world apply paths for protect rollback / restore. */
final class ProtectApplyOps {

    private final JavaPlugin plugin;

    ProtectApplyOps(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    Comparator<ProtectChange> rollbackOrder() {
        return (a, b) -> {
            boolean invA = a.changeType() == ChangeType.CONTAINER_INVENTORY;
            boolean invB = b.changeType() == ChangeType.CONTAINER_INVENTORY;
            if (invA && invB) {
                return Long.compare(b.epochMs(), a.epochMs());
            }
            if (invA != invB) {
                return invA ? -1 : 1;
            }
            return Long.compare(a.epochMs(), b.epochMs());
        };
    }

    Comparator<ProtectChange> restoreOrder() {
        return rollbackOrder().reversed();
    }

    boolean applyChangeRollback(ProtectChange change) {
        if (!ProtectApplyRules.canRollback(change.changeType())) {
            return false;
        }
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE, EXPLOSION, LIQUID_FLOW, FIRE ->
                    applyBlockState(change, change.blockBefore());
            case CONTAINER_INVENTORY -> applyInventoryState(change, change.blockBefore());
            default -> false;
        };
    }

    boolean applyChangeRestore(ProtectChange change) {
        if (!ProtectApplyRules.canRestore(change.changeType())) {
            return false;
        }
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE, EXPLOSION, LIQUID_FLOW, FIRE ->
                    applyBlockState(change, change.blockAfter());
            case CONTAINER_INVENTORY -> applyInventoryState(change, change.blockAfter());
            default -> false;
        };
    }

    private boolean applyBlockState(ProtectChange change, String encoded) {
        World world = Bukkit.getWorld(change.world());
        if (world == null) {
            return false;
        }
        Location loc = new Location(world, change.x(), change.y(), change.z());
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                Block block = loc.getBlock();
                BlockCodec.apply(block, encoded);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        try {
            return done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyInventoryState(ProtectChange change, String encoded) {
        World world = Bukkit.getWorld(change.world());
        if (world == null) {
            return false;
        }
        Location loc = new Location(world, change.x(), change.y(), change.z());
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                BlockState state = loc.getBlock().getState();
                if (!(state instanceof Container container)) {
                    done.complete(false);
                    return;
                }
                Inventory inventory = container.getInventory();
                InventoryCodec.apply(inventory, encoded);
                state.update(true, false);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        try {
            return done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
