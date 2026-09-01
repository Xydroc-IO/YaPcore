package com.yapcore.world.edit;

import com.yapcore.world.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Cuboid set/replace/walls/shell/hollow/outline + analysis helpers. */
public final class SelectionEditService {

    private final BlockBatch batch;

    public SelectionEditService(JavaPlugin plugin, UndoService undo) {
        this.batch = new BlockBatch(plugin, undo);
    }

    public CompletableFuture<Integer> fill(Player player, CuboidSelection sel, Material material) {
        return fillPattern(player, sel, material == null ? "stone" : material.name());
    }

    public CompletableFuture<Integer> fillPattern(Player player, CuboidSelection sel, String pattern) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        List<BlockBatch.Planned> plans = new ArrayList<>();
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(),
                (x, y, z) -> plans.add(new BlockBatch.Planned(x, y, z, BlockBatch.pickPattern(pattern))));
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> replace(Player player, CuboidSelection sel, Material from, Material to) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        List<BlockBatch.Planned> plans = new ArrayList<>();
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(), (x, y, z) -> {
            if (world.getBlockAt(x, y, z).getType() == from) {
                plans.add(new BlockBatch.Planned(x, y, z, to));
            }
        });
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> walls(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material, (x, y, z) ->
                x == sel.minX() || x == sel.maxX() || z == sel.minZ() || z == sel.maxZ());
    }

    public CompletableFuture<Integer> shell(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material, (x, y, z) -> onShell(x, y, z, sel));
    }

    public CompletableFuture<Integer> hollow(Player player, CuboidSelection sel) {
        return mask(player, sel, Material.AIR, (x, y, z) -> !onShell(x, y, z, sel));
    }

    public CompletableFuture<Integer> outline(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material, (x, y, z) -> onOutline(x, y, z, sel));
    }

    public CompletableFuture<Integer> naturalize(Player player, CuboidSelection sel) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                Integer top = null;
                for (int y = sel.maxY(); y >= sel.minY(); y--) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!type.isAir()) {
                        top = y;
                        break;
                    }
                }
                if (top == null) {
                    continue;
                }
                plans.add(new BlockBatch.Planned(x, top, z, Material.GRASS_BLOCK));
                if (top - 1 >= sel.minY()) {
                    plans.add(new BlockBatch.Planned(x, top - 1, z, Material.DIRT));
                }
                if (top - 2 >= sel.minY()) {
                    plans.add(new BlockBatch.Planned(x, top - 2, z, Material.DIRT));
                }
                for (int y = top - 3; y >= sel.minY(); y--) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!type.isAir() && type != Material.BEDROCK) {
                        plans.add(new BlockBatch.Planned(x, y, z, Material.STONE));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public Map<Material, Integer> count(CuboidSelection sel) {
        World world = Bukkit.getWorld(sel.world());
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        if (world == null) {
            return counts;
        }
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(), (x, y, z) -> {
            Material type = world.getBlockAt(x, y, z).getType();
            counts.merge(type, 1, Integer::sum);
        });
        return counts;
    }

    public Map<String, Integer> distribution(CuboidSelection sel, int limit) {
        Map<Material, Integer> raw = count(sel);
        List<Map.Entry<Material, Integer>> sorted = new ArrayList<>(raw.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> out = new LinkedHashMap<>();
        int n = 0;
        for (var e : sorted) {
            if (n++ >= limit) {
                break;
            }
            out.put(e.getKey().name().toLowerCase(), e.getValue());
        }
        return out;
    }

    private CompletableFuture<Integer> mask(Player player, CuboidSelection sel, Material material, CoordFilter filter) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        Material target = material == null || !material.isBlock() ? Material.STONE : material;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(), (x, y, z) -> {
            if (filter.test(x, y, z)) {
                plans.add(new BlockBatch.Planned(x, y, z, target));
            }
        });
        return batch.apply(player, world, plans);
    }

    @FunctionalInterface
    private interface CoordFilter {
        boolean test(int x, int y, int z);
    }

    private static boolean onShell(int x, int y, int z, CuboidSelection sel) {
        return x == sel.minX() || x == sel.maxX()
                || y == sel.minY() || y == sel.maxY()
                || z == sel.minZ() || z == sel.maxZ();
    }

    private static boolean onOutline(int x, int y, int z, CuboidSelection sel) {
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
}
