package com.yapcore.world.edit;

import com.yapcore.world.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Cylinders, spheres, pyramids, overlay, drain, line, smooth. */
public final class GenerationService {

    private final JavaPlugin plugin;
    private final BlockBatch batch;

    public GenerationService(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.batch = new BlockBatch(plugin, undo);
    }

    public CompletableFuture<Integer> cylinder(Player player, Location center, String pattern,
                                               int radius, int height, boolean hollow) {
        World world = center.getWorld();
        if (world == null || radius < 0 || height < 1) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = radius * radius;
        int outer = rSq;
        int inner = hollow && radius > 0 ? (radius - 1) * (radius - 1) : -1;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d = x * x + z * z;
                if (d > outer || (hollow && d <= inner && radius > 0)) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, BlockBatch.pickPattern(pattern)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> sphere(Player player, Location center, String pattern,
                                             int radius, boolean hollow) {
        World world = center.getWorld();
        if (world == null || radius < 0) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = radius * radius;
        int inner = hollow && radius > 0 ? (radius - 1) * (radius - 1) : -1;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int d = x * x + y * y + z * z;
                    if (d > rSq || (hollow && d <= inner)) {
                        continue;
                    }
                    plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, BlockBatch.pickPattern(pattern)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> pyramid(Player player, Location center, String pattern, int size, boolean hollow) {
        World world = center.getWorld();
        if (world == null || size < 1) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            int layer = size - y - 1;
            for (int x = -layer; x <= layer; x++) {
                for (int z = -layer; z <= layer; z++) {
                    if (hollow && Math.abs(x) < layer && Math.abs(z) < layer && y < size - 1) {
                        continue;
                    }
                    plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, BlockBatch.pickPattern(pattern)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> overlay(Player player, CuboidSelection sel, String pattern) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                for (int y = sel.maxY(); y >= sel.minY(); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        if (y + 1 <= world.getMaxHeight() - 1) {
                            plans.add(new BlockBatch.Planned(x, y + 1, z, BlockBatch.pickPattern(pattern)));
                        }
                        break;
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> drain(Player player, Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = radius * radius;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    Material type = block.getType();
                    if (type == Material.WATER || type == Material.LAVA
                            || type == Material.BUBBLE_COLUMN || type.name().endsWith("_WATER")) {
                        plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, Material.AIR));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> line(Player player, Location a, Location b, String pattern) {
        World world = a.getWorld();
        if (world == null || b.getWorld() == null || !world.equals(b.getWorld())) {
            return CompletableFuture.completedFuture(0);
        }
        int x1 = a.getBlockX();
        int y1 = a.getBlockY();
        int z1 = a.getBlockZ();
        int x2 = b.getBlockX();
        int y2 = b.getBlockY();
        int z2 = b.getBlockZ();
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        int dm = Math.max(dx, Math.max(dy, dz));
        List<BlockBatch.Planned> plans = new ArrayList<>();
        int x = x1;
        int y = y1;
        int z = z1;
        int ex = 0;
        int ey = 0;
        int ez = 0;
        for (int i = 0; i <= dm; i++) {
            plans.add(new BlockBatch.Planned(x, y, z, BlockBatch.pickPattern(pattern)));
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

    public CompletableFuture<Integer> smooth(Player player, CuboidSelection sel, int iterations) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int iters = Math.max(1, Math.min(iterations, 5));
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (int i = 0; i < iters; i++) {
            chain = chain.thenCompose(total -> smoothOnce(player, world, sel)
                    .thenApply(n -> total + n));
        }
        return chain;
    }

    private CompletableFuture<Integer> smoothOnce(Player player, World world, CuboidSelection sel) {
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                int sum = 0;
                int count = 0;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int hx = highestSolid(world, x + ox, z + oz, sel.minY(), sel.maxY());
                        if (hx != Integer.MIN_VALUE) {
                            sum += hx;
                            count++;
                        }
                    }
                }
                if (count == 0) {
                    continue;
                }
                int targetY = Math.round(sum / (float) count);
                int current = highestSolid(world, x, z, sel.minY(), sel.maxY());
                if (current == Integer.MIN_VALUE) {
                    continue;
                }
                if (targetY > current) {
                    Material fill = world.getBlockAt(x, current, z).getType();
                    for (int y = current + 1; y <= targetY && y <= sel.maxY(); y++) {
                        plans.add(new BlockBatch.Planned(x, y, z, fill.isAir() ? Material.STONE : fill));
                    }
                } else if (targetY < current) {
                    for (int y = current; y > targetY && y >= sel.minY(); y--) {
                        plans.add(new BlockBatch.Planned(x, y, z, Material.AIR));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    private static int highestSolid(World world, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public CompletableFuture<Integer> replaceNear(Player player, Location center, Material from, Material to, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = radius * radius;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (block.getType() == from) {
                        plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, to));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public CompletableFuture<Integer> removeAboveBelow(Player player, Location center, int height, boolean above) {
        World world = center.getWorld();
        if (world == null || height < 1) {
            return CompletableFuture.completedFuture(0);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<BlockBatch.Planned> plans = new ArrayList<>();
        if (above) {
            for (int y = 1; y <= height; y++) {
                plans.add(new BlockBatch.Planned(cx, cy + y, cz, Material.AIR));
            }
        } else {
            for (int y = 1; y <= height; y++) {
                plans.add(new BlockBatch.Planned(cx, cy - y, cz, Material.AIR));
            }
        }
        // Also clear a 3x3 column for usability
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                for (int y = 1; y <= height; y++) {
                    int yy = above ? cy + y : cy - y;
                    plans.add(new BlockBatch.Planned(cx + x, yy, cz + z, Material.AIR));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    public static Vector facingVector(Player player) {
        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();
        if (Math.abs(pitch) > 60) {
            return pitch > 0 ? new Vector(0, -1, 0) : new Vector(0, 1, 0);
        }
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) {
            return new Vector(0, 0, 1);
        }
        if (rot < 135) {
            return new Vector(-1, 0, 0);
        }
        if (rot < 225) {
            return new Vector(0, 0, -1);
        }
        return new Vector(1, 0, 0);
    }
}
