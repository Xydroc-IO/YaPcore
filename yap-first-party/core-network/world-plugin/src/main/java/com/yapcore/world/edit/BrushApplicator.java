package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Brush apply implementations for {@link BrushService}. */
final class BrushApplicator {

    private final JavaPlugin plugin;
    private final BlockBatch batch;
    private final Supplier<MaskEngine> masks;
    private final Supplier<ClipboardService> clipboard;

    BrushApplicator(JavaPlugin plugin, BlockBatch batch,
                    Supplier<MaskEngine> masks, Supplier<ClipboardService> clipboard) {
        this.plugin = plugin;
        this.batch = batch;
        this.masks = masks;
        this.clipboard = clipboard;
    }

    CompletableFuture<Integer> applySphere(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        PatternEngine.Pattern pat = PatternEngine.parse(state.pattern());
        List<BlockBatch.Planned> plans = new ArrayList<>();
        UUID id = player.getUniqueId();
        MaskEngine mask = masks.get();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    if (mask != null && !mask.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyCyl(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        int height = Math.max(1, r);
        PatternEngine.Pattern pat = PatternEngine.parse(state.pattern());
        List<BlockBatch.Planned> plans = new ArrayList<>();
        UUID id = player.getUniqueId();
        MaskEngine mask = masks.get();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > rSq) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    if (mask != null && !mask.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applySmooth(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int minX = center.getBlockX() - r;
        int maxX = center.getBlockX() + r;
        int minZ = center.getBlockZ() - r;
        int maxZ = center.getBlockZ() + r;
        int minY = center.getBlockY() - r;
        int maxY = center.getBlockY() + r;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int sum = 0;
                int count = 0;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int hx = highestSolid(world, x + ox, z + oz, minY, maxY);
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
                int current = highestSolid(world, x, z, minY, maxY);
                if (current == Integer.MIN_VALUE) {
                    continue;
                }
                if (targetY > current) {
                    Material fill = world.getBlockAt(x, current, z).getType();
                    for (int y = current + 1; y <= targetY && y <= maxY; y++) {
                        plans.add(new BlockBatch.Planned(x, y, z, fill.isAir() ? Material.STONE : fill));
                    }
                } else if (targetY < current) {
                    for (int y = current; y > targetY && y >= minY; y--) {
                        plans.add(new BlockBatch.Planned(x, y, z, Material.AIR));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyGravity(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<BlockBatch.Encoded> dest = new ArrayList<>();
        List<BlockBatch.Planned> clear = new ArrayList<>();
        CompletableFuture<Integer> done = new CompletableFuture<>();
        YapSched.region(plugin, center, () -> {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) {
                        continue;
                    }
                    List<String> column = new ArrayList<>();
                    for (int y = -r; y <= r; y++) {
                        var b = world.getBlockAt(cx + x, cy + y, cz + z);
                        if (!b.getType().isAir()) {
                            column.add(BlockCodec.encode(b));
                            clear.add(new BlockBatch.Planned(cx + x, cy + y, cz + z, Material.AIR));
                        }
                    }
                    int y = cy - r;
                    for (String enc : column) {
                        final int fx = cx + x;
                        final int fz = cz + z;
                        while (y <= cy + r) {
                            final int fy = y;
                            boolean occupied = !world.getBlockAt(fx, fy, fz).getType().isAir()
                                    && clear.stream().noneMatch(p -> p.x() == fx && p.y() == fy && p.z() == fz);
                            if (!occupied) {
                                break;
                            }
                            y++;
                        }
                        if (y > cy + r) {
                            break;
                        }
                        dest.add(new BlockBatch.Encoded(fx, y, fz, enc));
                        y++;
                    }
                }
            }
            batch.apply(player, world, clear).thenCompose(n -> batch.applyEncoded(player, world, dest))
                    .whenComplete((n, err) -> done.complete(n == null ? 0 : n));
        });
        return done;
    }

    CompletableFuture<Integer> applyClipboard(Player player, Location center, BrushService.BrushState state) {
        ClipboardService clips = clipboard.get();
        if (clips == null || clips.clipboard(player.getUniqueId()) == null) {
            return CompletableFuture.completedFuture(0);
        }
        ClipboardService.Clipboard clip = clips.clipboard(player.getUniqueId());
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int ox = center.getBlockX() - clip.offsetX();
        int oy = center.getBlockY() - clip.offsetY();
        int oz = center.getBlockZ() - clip.offsetZ();
        List<BlockBatch.Encoded> plans = new ArrayList<>();
        for (Schematic.BlockEntry e : clip.blocks()) {
            plans.add(new BlockBatch.Encoded(ox + e.dx(), oy + e.dy(), oz + e.dz(), e.encoded()));
        }
        return batch.applyEncoded(player, world, plans);
    }

    CompletableFuture<Integer> applyButcher(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        double r = state.radius();
        YapSched.region(plugin, center, () -> {
            int n = 0;
            for (Entity e : world.getNearbyEntities(center, r, r, r)) {
                if (e instanceof LivingEntity living && !(e instanceof Player)) {
                    living.remove();
                    n++;
                }
            }
            done.complete(n);
        });
        return done;
    }

    CompletableFuture<Integer> applyErode(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    var b = world.getBlockAt(wx, wy, wz);
                    if (b.getType().isAir()) {
                        continue;
                    }
                    int airNeighbors = 0;
                    if (world.getBlockAt(wx + 1, wy, wz).getType().isAir()) airNeighbors++;
                    if (world.getBlockAt(wx - 1, wy, wz).getType().isAir()) airNeighbors++;
                    if (world.getBlockAt(wx, wy + 1, wz).getType().isAir()) airNeighbors++;
                    if (world.getBlockAt(wx, wy - 1, wz).getType().isAir()) airNeighbors++;
                    if (world.getBlockAt(wx, wy, wz + 1).getType().isAir()) airNeighbors++;
                    if (world.getBlockAt(wx, wy, wz - 1).getType().isAir()) airNeighbors++;
                    if (airNeighbors >= 2) {
                        plans.add(new BlockBatch.Planned(wx, wy, wz, Material.AIR));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyRaiseLower(Player player, Location center, BrushService.BrushState state, int dir) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<BlockBatch.Planned> plans = new ArrayList<>();
        PatternEngine.Pattern pat = PatternEngine.parse(state.pattern());
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                int hx = highestSolid(world, cx + x, cz + z, cy - r, cy + r);
                if (hx == Integer.MIN_VALUE) {
                    continue;
                }
                if (dir > 0) {
                    int ty = hx + 1;
                    if (ty <= cy + r) {
                        plans.add(PatternEngine.toBatch(cx + x, ty, cz + z,
                                pat.resolve(world, cx + x, ty, cz + z, null)));
                    }
                } else {
                    plans.add(new BlockBatch.Planned(cx + x, hx, cz + z, Material.AIR));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyMelt(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        List<BlockBatch.Planned> plans = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    Material t = world.getBlockAt(cx + x, cy + y, cz + z).getType();
                    if (t == Material.SNOW || t == Material.SNOW_BLOCK || t == Material.ICE
                            || t == Material.PACKED_ICE || t == Material.BLUE_ICE
                            || t == Material.FROSTED_ICE) {
                        plans.add(new BlockBatch.Planned(cx + x, cy + y, cz + z,
                                t == Material.SNOW ? Material.AIR : Material.WATER));
                    }
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyFill(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        PatternEngine.Pattern pat = PatternEngine.parse(state.pattern());
        List<BlockBatch.Planned> plans = new ArrayList<>();
        UUID id = player.getUniqueId();
        MaskEngine mask = masks.get();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = -r; y <= 0; y++) {
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    if (!world.getBlockAt(wx, wy, wz).getType().isAir()) {
                        continue;
                    }
                    if (mask != null && !mask.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    CompletableFuture<Integer> applyForest(Player player, Location center, BrushService.BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        int r = state.radius();
        YapSched.region(plugin, center, () -> {
            int planted = 0;
            int cx = center.getBlockX();
            int cz = center.getBlockZ();
            int cy = center.getBlockY();
            for (int x = -r; x <= r; x += 2) {
                for (int z = -r; z <= r; z += 2) {
                    if (x * x + z * z > r * r) {
                        continue;
                    }
                    int hx = highestSolid(world, cx + x, cz + z, cy - r, cy + r);
                    if (hx == Integer.MIN_VALUE) {
                        continue;
                    }
                    Location at = new Location(world, cx + x, hx + 1, cz + z);
                    if (world.generateTree(at, org.bukkit.TreeType.TREE)) {
                        planted++;
                    }
                }
            }
            done.complete(planted);
        });
        return done;
    }

    static int highestSolid(World world, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
