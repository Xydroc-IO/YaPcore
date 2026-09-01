package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/** Cuboid fill/replace operations on the current selection. */
public final class SelectionEditService {

    private final JavaPlugin plugin;
    private final UndoService undo;

    public SelectionEditService(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.undo = undo;
    }

    public CompletableFuture<Integer> fill(Player player, CuboidSelection sel, Material material) {
        Material target = validBlock(material);
        return apply(player, sel, (x, y, z) -> true, target, false, null);
    }

    public CompletableFuture<Integer> replace(Player player, CuboidSelection sel, Material from, Material to) {
        Material source = validBlock(from);
        Material target = validBlock(to);
        return apply(player, sel, (x, y, z) -> true, target, true, source);
    }

    /** Four vertical walls (full height), open top and bottom. */
    public CompletableFuture<Integer> walls(Player player, CuboidSelection sel, Material material) {
        Material target = validBlock(material);
        return apply(player, sel, (x, y, z) ->
                x == sel.minX() || x == sel.maxX() || z == sel.minZ() || z == sel.maxZ(), target, false, null);
    }

    /** All six faces of the cuboid. */
    public CompletableFuture<Integer> shell(Player player, CuboidSelection sel, Material material) {
        Material target = validBlock(material);
        return apply(player, sel, (x, y, z) -> onShell(x, y, z, sel), target, false, null);
    }

    /** Clear interior blocks; leaves the outer shell unchanged. */
    public CompletableFuture<Integer> hollow(Player player, CuboidSelection sel) {
        return apply(player, sel, (x, y, z) -> !onShell(x, y, z, sel), Material.AIR, false, null);
    }

    /** One-block-thick outline along all twelve edges. */
    public CompletableFuture<Integer> outline(Player player, CuboidSelection sel, Material material) {
        Material target = validBlock(material);
        return apply(player, sel, (x, y, z) -> onOutline(x, y, z, sel), target, false, null);
    }

    private CompletableFuture<Integer> apply(Player player, CuboidSelection sel,
                                             CoordFilter filter, Material material,
                                             boolean onlyMatching, Material matchMaterial) {
        World world = org.bukkit.Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        EditSession session = new EditSession();
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int y = sel.minY(); y <= sel.maxY(); y++) {
                for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                    if (!filter.test(x, y, z)) {
                        continue;
                    }
                    int bx = x;
                    int by = y;
                    int bz = z;
                    chain = chain.thenCompose(count -> setOne(session, world, bx, by, bz, material, onlyMatching, matchMaterial)
                            .thenApply(ok -> ok ? count + 1 : count));
                }
            }
        }
        return chain.thenApply(count -> {
            undo.push(player.getUniqueId(), session);
            return count;
        });
    }

    @FunctionalInterface
    private interface CoordFilter {
        boolean test(int x, int y, int z);
    }

    private CompletableFuture<Boolean> setOne(EditSession session, World world, int x, int y, int z,
                                              Material material, boolean onlyMatching, Material matchMaterial) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        var loc = new org.bukkit.Location(world, x, y, z);
        YapSched.region(plugin, loc, () -> {
            try {
                Block block = world.getBlockAt(x, y, z);
                if (onlyMatching && block.getType() != matchMaterial) {
                    done.complete(false);
                    return;
                }
                String before = BlockCodec.encode(block);
                block.setType(material, false);
                String after = BlockCodec.encode(block);
                session.record(world.getName(), x, y, z, before, after);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        return done;
    }

    private boolean onShell(int x, int y, int z, CuboidSelection sel) {
        return x == sel.minX() || x == sel.maxX()
                || y == sel.minY() || y == sel.maxY()
                || z == sel.minZ() || z == sel.maxZ();
    }

    private boolean onOutline(int x, int y, int z, CuboidSelection sel) {
        int edges = 0;
        if (x == sel.minX() || x == sel.maxX()) {
            edges++;
        }
        if (y == sel.minY() || y == sel.maxY()) {
            edges++;
        }
        if (z == sel.minZ() || z == sel.maxZ()) {
            edges++;
        }
        return edges >= 2;
    }

    private static Material validBlock(Material material) {
        return material == null || !material.isBlock() ? Material.STONE : material;
    }
}
