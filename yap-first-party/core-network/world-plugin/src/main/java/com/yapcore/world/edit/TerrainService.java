package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Terrain ops: regen, forest, flora, biomes, deform, twist, center, curve, tree. */
public final class TerrainService {

    private final JavaPlugin plugin;
    private final BlockBatch batch;
    private MaskEngine masks;
    private SelectionShape shapes;

    public TerrainService(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.batch = new BlockBatch(plugin, undo);
    }

    public void setMasks(MaskEngine masks) {
        this.masks = masks;
    }

    public void setShapes(SelectionShape shapes) {
        this.shapes = shapes;
    }

    public void setEditState(PlayerEditState state) {
        batch.setEditState(state);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    public CompletableFuture<Integer> setBiome(Player player, CuboidSelection sel, String biomeName) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        Biome biome = matchBiome(biomeName);
        if (biome == null) {
            return CompletableFuture.completedFuture(-1);
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        YapSched.region(plugin, new Location(world, sel.minX(), sel.minY(), sel.minZ()), () -> {
            int n = 0;
            var id = player.getUniqueId();
            for (int x = sel.minX(); x <= sel.maxX(); x++) {
                for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                    for (int y = sel.minY(); y <= sel.maxY(); y += 4) {
                        if (shapes != null && !shapes.contains(id, sel, x, y, z)) {
                            continue;
                        }
                        if (masks != null && !masks.allows(id, world, x, y, z)) {
                            continue;
                        }
                        world.setBiome(x, y, z, biome);
                        n++;
                    }
                }
            }
            done.complete(n);
        });
        return done;
    }

    public String biomeAt(Location loc) {
        if (loc.getWorld() == null) {
            return "unknown";
        }
        return loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getKey().getKey();
    }

    public List<String> biomeList(int limit) {
        List<String> out = new ArrayList<>();
        for (Biome b : Registry.BIOME) {
            out.add(b.getKey().getKey());
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    public CompletableFuture<Integer> regen(Player player, CuboidSelection sel) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        // Soft regen: clear non-air then let nature-like fill with stone/dirt/grass layers
        List<BlockBatch.Planned> plans = new ArrayList<>();
        var id = player.getUniqueId();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                int surface = sel.minY() + (sel.maxY() - sel.minY()) * 2 / 3;
                for (int y = sel.minY(); y <= sel.maxY(); y++) {
                    if (shapes != null && !shapes.contains(id, sel, x, y, z)) {
                        continue;
                    }
                    Material mat;
                    if (y > surface) {
                        mat = Material.AIR;
                    } else if (y == surface) {
                        mat = Material.GRASS_BLOCK;
                    } else if (y > surface - 3) {
                        mat = Material.DIRT;
                    } else {
                        mat = Material.STONE;
                    }
                    plans.add(new BlockBatch.Planned(x, y, z, mat));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> forest(Player player, CuboidSelection sel, double density) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        double d = Math.max(0.01, Math.min(density, 1.0));
        CompletableFuture<Integer> done = new CompletableFuture<>();
        YapSched.region(plugin, new Location(world, sel.minX(), sel.minY(), sel.minZ()), () -> {
            int planted = 0;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int x = sel.minX(); x <= sel.maxX(); x += 2) {
                for (int z = sel.minZ(); z <= sel.maxZ(); z += 2) {
                    if (rng.nextDouble() > d) {
                        continue;
                    }
                    Integer y = topSolid(world, x, z, sel.minY(), sel.maxY());
                    if (y == null) {
                        continue;
                    }
                    Location at = new Location(world, x, y + 1, z);
                    if (world.generateTree(at, TreeType.TREE)) {
                        planted++;
                    }
                }
            }
            done.complete(planted);
        });
        return done;
    }

    public CompletableFuture<Integer> flora(Player player, CuboidSelection sel, double density) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        Material[] flora = {
                Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
                Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY
        };
        List<BlockBatch.Planned> plans = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double d = Math.max(0.01, Math.min(density, 1.0));
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                if (rng.nextDouble() > d) {
                    continue;
                }
                Integer y = topSolid(world, x, z, sel.minY(), sel.maxY());
                if (y == null || y + 1 > sel.maxY()) {
                    continue;
                }
                if (!world.getBlockAt(x, y + 1, z).getType().isAir()) {
                    continue;
                }
                plans.add(new BlockBatch.Planned(x, y + 1, z, flora[rng.nextInt(flora.length)]));
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> pumpkins(Player player, CuboidSelection sel, double density) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        List<BlockBatch.Planned> plans = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double d = Math.max(0.01, Math.min(density, 1.0));
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                if (rng.nextDouble() > d) {
                    continue;
                }
                Integer y = topSolid(world, x, z, sel.minY(), sel.maxY());
                if (y == null || y + 1 > sel.maxY()) {
                    continue;
                }
                plans.add(new BlockBatch.Planned(x, y + 1, z, Material.PUMPKIN));
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> deform(Player player, CuboidSelection sel, String expression) {
        // Supported: raise N, lower N, sine, noise
        String expr = expression == null ? "noise" : expression.toLowerCase(Locale.ROOT);
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int amount = 2;
        if (expr.startsWith("raise")) {
            amount = parseTail(expr, 2);
            return shiftHeights(player, world, sel, amount);
        }
        if (expr.startsWith("lower")) {
            amount = parseTail(expr, 2);
            return shiftHeights(player, world, sel, -amount);
        }
        return shiftHeights(player, world, sel, 0); // noise path
    }

    private CompletableFuture<Integer> shiftHeights(Player player, World world, CuboidSelection sel, int delta) {
        List<BlockBatch.Planned> plans = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                int d = delta != 0 ? delta : rng.nextInt(-2, 3);
                if (d == 0) {
                    continue;
                }
                Integer top = topSolid(world, x, z, sel.minY(), sel.maxY());
                if (top == null) {
                    continue;
                }
                Material fill = world.getBlockAt(x, top, z).getType();
                if (d > 0) {
                    for (int y = 1; y <= d && top + y <= sel.maxY(); y++) {
                        plans.add(new BlockBatch.Planned(x, top + y, z, fill.isAir() ? Material.STONE : fill));
                    }
                } else {
                    for (int y = 0; y < -d && top - y >= sel.minY(); y++) {
                        plans.add(new BlockBatch.Planned(x, top - y, z, Material.AIR));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> twist(Player player, CuboidSelection sel, double degrees) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        double cx = (sel.minX() + sel.maxX()) / 2.0;
        double cz = (sel.minZ() + sel.maxZ()) / 2.0;
        double rad = Math.toRadians(degrees);
        CompletableFuture<Integer> done = new CompletableFuture<>();
        YapSched.region(plugin, new Location(world, sel.minX(), sel.minY(), sel.minZ()), () -> {
            List<BlockBatch.Planned> clear = new ArrayList<>();
            List<BlockBatch.Encoded> dest = new ArrayList<>();
            for (int x = sel.minX(); x <= sel.maxX(); x++) {
                for (int y = sel.minY(); y <= sel.maxY(); y++) {
                    for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType().isAir()) {
                            continue;
                        }
                        String enc = com.yapcore.world.util.BlockCodec.encode(b);
                        double dx = x - cx;
                        double dz = z - cz;
                        int nx = (int) Math.round(cx + dx * Math.cos(rad) - dz * Math.sin(rad));
                        int nz = (int) Math.round(cz + dx * Math.sin(rad) + dz * Math.cos(rad));
                        clear.add(new BlockBatch.Planned(x, y, z, Material.AIR));
                        dest.add(new BlockBatch.Encoded(nx, y, nz, enc));
                    }
                }
            }
            batch.apply(player, world, clear).thenCompose(n -> batch.applyEncoded(player, world, dest))
                    .whenComplete((n, err) -> done.complete(n == null ? 0 : n));
        });
        return done;
    }

    public Location center(CuboidSelection sel) {
        return new Location(Bukkit.getWorld(sel.world()),
                (sel.minX() + sel.maxX()) / 2.0 + 0.5,
                (sel.minY() + sel.maxY()) / 2.0,
                (sel.minZ() + sel.maxZ()) / 2.0 + 0.5);
    }

    public CompletableFuture<Integer> curve(Player player, CuboidSelection sel, String pattern) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        Location a = new Location(world, sel.minX(), sel.minY(), sel.minZ());
        Location mid = new Location(world,
                (sel.minX() + sel.maxX()) / 2.0,
                sel.maxY(),
                (sel.minZ() + sel.maxZ()) / 2.0);
        Location b = new Location(world, sel.maxX(), sel.minY(), sel.maxZ());
        return lineSegment(player, world, a, mid, pattern)
                .thenCompose(n -> lineSegment(player, world, mid, b, pattern).thenApply(m -> n + m));
    }

    private CompletableFuture<Integer> lineSegment(Player player, World world, Location a, Location b, String pattern) {
        int x1 = a.getBlockX(), y1 = a.getBlockY(), z1 = a.getBlockZ();
        int x2 = b.getBlockX(), y2 = b.getBlockY(), z2 = b.getBlockZ();
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int dm = Math.max(dx, Math.max(dy, dz));
        List<BlockBatch.Planned> plans = new ArrayList<>();
        int x = x1, y = y1, z = z1, ex = 0, ey = 0, ez = 0;
        PatternEngine.Pattern pat = PatternEngine.parse(pattern);
        for (int i = 0; i <= dm; i++) {
            plans.add(PatternEngine.toBatch(x, y, z, pat.resolve(world, x, y, z, null)));
            ex += dx;
            if (2 * ex >= dm) {
                x += sx;
                ex -= dm;
            }
            ey += dy;
            if (2 * ey >= dm) {
                y += sy;
                ey -= dm;
            }
            ez += dz;
            if (2 * ez >= dm) {
                z += sz;
                ez -= dm;
            }
        }
        return batch.apply(player, world, plans);
    }

    public boolean plantTree(Player player, Location loc, String typeName) {
        TreeType type = switch (typeName == null ? "oak" : typeName.toLowerCase(Locale.ROOT)) {
            case "birch" -> TreeType.BIRCH;
            case "spruce", "pine" -> TreeType.REDWOOD;
            case "jungle" -> TreeType.SMALL_JUNGLE;
            case "acacia" -> TreeType.ACACIA;
            case "dark_oak", "darkoak" -> TreeType.DARK_OAK;
            case "chorus" -> TreeType.CHORUS_PLANT;
            default -> TreeType.TREE;
        };
        return loc.getWorld() != null && loc.getWorld().generateTree(loc, type);
    }

    private static Integer topSolid(World world, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir()) {
                return y;
            }
        }
        return null;
    }

    private static int parseTail(String expr, int def) {
        String[] p = expr.split("\\s+");
        if (p.length >= 2) {
            try {
                return Integer.parseInt(p[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static Biome matchBiome(String name) {
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        for (Biome b : Registry.BIOME) {
            if (b.getKey().getKey().equalsIgnoreCase(key)) {
                return b;
            }
        }
        return null;
    }
}
