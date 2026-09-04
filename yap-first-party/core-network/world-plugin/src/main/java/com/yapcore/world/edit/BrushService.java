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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BrushService {

    public static final Material BRUSH_TOOL = Material.BLAZE_ROD;

    public enum BrushType {
        SPHERE,
        CYL,
        SMOOTH,
        GRAVITY,
        CLIPBOARD,
        BUTCHER,
        ERODE,
        RAISE,
        LOWER,
        MELT,
        FILL,
        FOREST
    }

    private final JavaPlugin plugin;
    private final UndoService undo;
    private final BlockBatch batch;
    private final Map<UUID, BrushState> states = new ConcurrentHashMap<>();
    private MaskEngine masks;
    private PlayerEditState editState;
    private ClipboardService clipboard;
    private int maxRadius = 16;

    public BrushService(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.undo = undo;
        this.batch = new BlockBatch(plugin, undo);
    }

    public void setMasks(MaskEngine masks) {
        this.masks = masks;
    }

    public void setEditState(PlayerEditState editState) {
        this.editState = editState;
        batch.setEditState(editState);
    }

    public void setClipboard(ClipboardService clipboard) {
        this.clipboard = clipboard;
    }

    public void setMaxRadius(int maxRadius) {
        this.maxRadius = Math.max(1, Math.min(64, maxRadius));
        batch.setParallelChunks(4);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    public void setBrush(UUID playerId, int radius, Material material) {
        BrushState prev = states.get(playerId);
        BrushType type = prev == null ? BrushType.SPHERE : prev.type();
        String pattern = material == null || material.isAir() ? "stone" : material.name().toLowerCase(Locale.ROOT);
        if (editState != null) {
            editState.setBrushPattern(playerId, pattern);
        }
        states.put(playerId, new BrushState(
                clampRadius(radius),
                material == null || material.isAir() ? Material.STONE : material,
                type,
                pattern));
    }

    public void setBrushFull(UUID playerId, BrushType type, int radius, String pattern) {
        Material mat = PatternEngine.pickMaterial(pattern);
        if (editState != null) {
            editState.setBrushPattern(playerId, pattern);
        }
        states.put(playerId, new BrushState(clampRadius(radius), mat, type, pattern));
    }

    public void setBrushType(UUID playerId, String typeName) {
        BrushState prev = states.get(playerId);
        if (prev == null) {
            setBrush(playerId, 3, Material.STONE);
            prev = states.get(playerId);
        }
        BrushType type = parseType(typeName);
        states.put(playerId, new BrushState(prev.radius(), prev.material(), type, prev.pattern()));
    }

    public void setSize(UUID playerId, int radius) {
        BrushState prev = states.get(playerId);
        if (prev == null) {
            setBrush(playerId, radius, Material.STONE);
            return;
        }
        states.put(playerId, new BrushState(clampRadius(radius), prev.material(), prev.type(), prev.pattern()));
    }

    public void setMat(UUID playerId, String pattern) {
        BrushState prev = states.get(playerId);
        if (prev == null) {
            setBrushFull(playerId, BrushType.SPHERE, 3, pattern);
            return;
        }
        Material mat = PatternEngine.pickMaterial(pattern);
        if (editState != null) {
            editState.setBrushPattern(playerId, pattern);
        }
        states.put(playerId, new BrushState(prev.radius(), mat, prev.type(), pattern));
    }

    public BrushState state(UUID playerId) {
        return states.get(playerId);
    }

    public CompletableFuture<Integer> apply(Player player, Location center) {
        BrushState state = states.get(player.getUniqueId());
        if (state == null) {
            return CompletableFuture.completedFuture(0);
        }
        return switch (state.type()) {
            case CYL -> applyCyl(player, center, state);
            case SMOOTH -> applySmooth(player, center, state);
            case GRAVITY -> applyGravity(player, center, state);
            case CLIPBOARD -> applyClipboard(player, center, state);
            case BUTCHER -> applyButcher(player, center, state);
            case ERODE -> applyErode(player, center, state);
            case RAISE -> applyRaiseLower(player, center, state, 1);
            case LOWER -> applyRaiseLower(player, center, state, -1);
            case MELT -> applyMelt(player, center, state);
            case FILL -> applyFill(player, center, state);
            case FOREST -> applyForest(player, center, state);
            default -> applySphere(player, center, state);
        };
    }

    public CompletableFuture<Integer> applySphere(Player player, Location center) {
        BrushState state = states.get(player.getUniqueId());
        if (state == null) {
            return CompletableFuture.completedFuture(0);
        }
        return applySphere(player, center, state);
    }

    private CompletableFuture<Integer> applySphere(Player player, Location center, BrushState state) {
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
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    if (masks != null && !masks.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    private CompletableFuture<Integer> applyCyl(Player player, Location center, BrushState state) {
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
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > rSq) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    int wx = cx + x, wy = cy + y, wz = cz + z;
                    if (masks != null && !masks.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    private CompletableFuture<Integer> applySmooth(Player player, Location center, BrushState state) {
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

    private CompletableFuture<Integer> applyGravity(Player player, Location center, BrushState state) {
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

    private CompletableFuture<Integer> applyClipboard(Player player, Location center, BrushState state) {
        if (clipboard == null || clipboard.clipboard(player.getUniqueId()) == null) {
            return CompletableFuture.completedFuture(0);
        }
        ClipboardService.Clipboard clip = clipboard.clipboard(player.getUniqueId());
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

    private CompletableFuture<Integer> applyButcher(Player player, Location center, BrushState state) {
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

    private CompletableFuture<Integer> applyErode(Player player, Location center, BrushState state) {
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

    private CompletableFuture<Integer> applyRaiseLower(Player player, Location center, BrushState state, int dir) {
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

    private CompletableFuture<Integer> applyMelt(Player player, Location center, BrushState state) {
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

    private CompletableFuture<Integer> applyFill(Player player, Location center, BrushState state) {
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
                    if (masks != null && !masks.allows(id, world, wx, wy, wz)) {
                        continue;
                    }
                    plans.add(PatternEngine.toBatch(wx, wy, wz, pat.resolve(world, wx, wy, wz, null)));
                }
            }
        }
        return batch.apply(player, world, plans);
    }

    private CompletableFuture<Integer> applyForest(Player player, Location center, BrushState state) {
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

    private int clampRadius(int radius) {
        return Math.max(1, Math.min(radius, maxRadius));
    }

    private static BrushType parseType(String typeName) {
        return switch (typeName == null ? "sphere" : typeName.toLowerCase(Locale.ROOT)) {
            case "cyl", "cylinder" -> BrushType.CYL;
            case "smooth" -> BrushType.SMOOTH;
            case "gravity", "grav" -> BrushType.GRAVITY;
            case "clipboard", "schem", "paste" -> BrushType.CLIPBOARD;
            case "butcher", "kill" -> BrushType.BUTCHER;
            case "erode" -> BrushType.ERODE;
            case "raise" -> BrushType.RAISE;
            case "lower" -> BrushType.LOWER;
            case "melt" -> BrushType.MELT;
            case "fill" -> BrushType.FILL;
            case "forest", "tree" -> BrushType.FOREST;
            default -> BrushType.SPHERE;
        };
    }

    private static int highestSolid(World world, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public record BrushState(int radius, Material material, BrushType type, String pattern) {
        public BrushState(int radius, Material material) {
            this(radius, material, BrushType.SPHERE, material == null ? "stone" : material.name().toLowerCase(Locale.ROOT));
        }

        public BrushState(int radius, Material material, BrushType type) {
            this(radius, material, type, material == null ? "stone" : material.name().toLowerCase(Locale.ROOT));
        }
    }
}
