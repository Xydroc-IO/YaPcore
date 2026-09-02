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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Cuboid/shape set/replace/walls/shell/hollow/outline + analysis helpers. */
public final class SelectionEditService {

    private final BlockBatch batch;
    private MaskEngine masks;
    private SelectionShape shapes;
    private long maxChanges = 2_000_000L;

    public SelectionEditService(JavaPlugin plugin, UndoService undo) {
        this.batch = new BlockBatch(plugin, undo);
    }

    public BlockBatch batch() {
        return batch;
    }

    public void setMasks(MaskEngine masks) {
        this.masks = masks;
    }

    public void setShapes(SelectionShape shapes) {
        this.shapes = shapes;
    }

    public void setMaxChanges(long maxChanges) {
        this.maxChanges = Math.max(1L, maxChanges);
    }

    public void setEditState(PlayerEditState state) {
        batch.setEditState(state);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    public CompletableFuture<Integer> fill(Player player, CuboidSelection sel, Material material) {
        return fillPattern(player, sel, material == null ? "stone" : material.name());
    }

    public CompletableFuture<Integer> fillPattern(Player player, CuboidSelection sel, String pattern) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID id = player.getUniqueId();
        if (masks != null) {
            masks.bindRegion(id, sel);
        }
        PatternEngine.Pattern pat = PatternEngine.parse(pattern);
        List<BlockBatch.Planned> plans = new ArrayList<>();
        List<BlockBatch.Encoded> encoded = new ArrayList<>();
        forEachShape(id, sel, (x, y, z) -> {
            if (plans.size() + encoded.size() >= maxChanges) {
                return;
            }
            if (masks != null && !masks.allows(id, world, x, y, z)) {
                return;
            }
            PatternEngine.Planned p = pat.resolve(world, x, y, z, null);
            if (p.encoded() != null) {
                encoded.add(new BlockBatch.Encoded(x, y, z, p.encoded()));
            } else {
                plans.add(PatternEngine.toBatch(x, y, z, p));
            }
        });
        if (encoded.isEmpty()) {
            return batch.apply(player, world, plans);
        }
        return batch.apply(player, world, plans).thenCompose(n ->
                batch.applyEncoded(player, world, encoded).thenApply(m -> n + m));
    }

    public CompletableFuture<Integer> replace(Player player, CuboidSelection sel, Material from, Material to) {
        return replaceMask(player, sel, from == null ? "air" : from.name(), to == null ? "stone" : to.name());
    }

    public CompletableFuture<Integer> replaceMask(Player player, CuboidSelection sel, String fromMask, String toPattern) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID id = player.getUniqueId();
        if (masks != null) {
            masks.bindRegion(id, sel);
        }
        MaskEngine.Mask from = MaskEngine.parseStatic(fromMask);
        PatternEngine.Pattern pat = PatternEngine.parse(toPattern);
        List<BlockBatch.Planned> plans = new ArrayList<>();
        forEachShape(id, sel, (x, y, z) -> {
            if (plans.size() >= maxChanges) {
                return;
            }
            if (masks != null && !masks.allows(id, world, x, y, z)) {
                return;
            }
            if (!from.test(world, x, y, z)) {
                return;
            }
            PatternEngine.Planned p = pat.resolve(world, x, y, z, null);
            plans.add(PatternEngine.toBatch(x, y, z, p));
        });
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> walls(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material == null ? "stone" : material.name(), (x, y, z) ->
                x == sel.minX() || x == sel.maxX() || z == sel.minZ() || z == sel.maxZ());
    }

    public CompletableFuture<Integer> wallsPattern(Player player, CuboidSelection sel, String pattern) {
        return mask(player, sel, pattern, (x, y, z) ->
                x == sel.minX() || x == sel.maxX() || z == sel.minZ() || z == sel.maxZ());
    }

    public CompletableFuture<Integer> shell(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material == null ? "stone" : material.name(), (x, y, z) -> onShell(x, y, z, sel));
    }

    public CompletableFuture<Integer> hollow(Player player, CuboidSelection sel) {
        return mask(player, sel, "air", (x, y, z) -> !onShell(x, y, z, sel));
    }

    public CompletableFuture<Integer> outline(Player player, CuboidSelection sel, Material material) {
        return mask(player, sel, material == null ? "stone" : material.name(), (x, y, z) -> onOutline(x, y, z, sel));
    }

    public CompletableFuture<Integer> naturalize(Player player, CuboidSelection sel) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID id = player.getUniqueId();
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                Integer top = null;
                for (int y = sel.maxY(); y >= sel.minY(); y--) {
                    if (shapes != null && !shapes.contains(id, sel, x, y, z)) {
                        continue;
                    }
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
        return count(sel, null, null);
    }

    public Map<Material, Integer> count(CuboidSelection sel, UUID playerId, MaskEngine.Mask extra) {
        World world = Bukkit.getWorld(sel.world());
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        if (world == null) {
            return counts;
        }
        forEachShape(playerId, sel, (x, y, z) -> {
            if (extra != null && !extra.test(world, x, y, z)) {
                return;
            }
            if (playerId != null && masks != null && !masks.allows(playerId, world, x, y, z)) {
                return;
            }
            Material type = world.getBlockAt(x, y, z).getType();
            counts.merge(type, 1, Integer::sum);
        });
        return counts;
    }

    public Map<String, Integer> distribution(CuboidSelection sel, int limit) {
        return distribution(sel, null, limit);
    }

    public Map<String, Integer> distribution(CuboidSelection sel, UUID playerId, int limit) {
        Map<Material, Integer> raw = count(sel, playerId, null);
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

    private CompletableFuture<Integer> mask(Player player, CuboidSelection sel, String pattern, CoordFilter filter) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID id = player.getUniqueId();
        PatternEngine.Pattern pat = PatternEngine.parse(pattern);
        List<BlockBatch.Planned> plans = new ArrayList<>();
        forEachShape(id, sel, (x, y, z) -> {
            if (!filter.test(x, y, z)) {
                return;
            }
            if (masks != null && !masks.allows(id, world, x, y, z)) {
                return;
            }
            plans.add(PatternEngine.toBatch(x, y, z, pat.resolve(world, x, y, z, null)));
        });
        return batch.apply(player, world, plans);
    }

    private void forEachShape(UUID id, CuboidSelection sel, BlockBatch.TriConsumer consumer) {
        if (shapes == null || id == null || shapes.mode(id) == SelectionShape.Mode.CUBOID) {
            BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(), consumer);
            return;
        }
        shapes.forEach(id, sel, xyz -> consumer.accept(xyz[0], xyz[1], xyz[2]));
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
