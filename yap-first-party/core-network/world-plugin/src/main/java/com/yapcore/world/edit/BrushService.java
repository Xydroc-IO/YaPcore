package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BrushService {

    public static final Material BRUSH_TOOL = Material.BLAZE_ROD;

    public enum BrushType {
        SPHERE,
        CYL
    }

    private final JavaPlugin plugin;
    private final UndoService undo;
    private final Map<UUID, BrushState> states = new ConcurrentHashMap<>();

    public BrushService(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.undo = undo;
    }

    public void setBrush(UUID playerId, int radius, Material material) {
        BrushState prev = states.get(playerId);
        BrushType type = prev == null ? BrushType.SPHERE : prev.type();
        states.put(playerId, new BrushState(
                Math.max(1, Math.min(radius, 32)),
                material == null || material.isAir() ? Material.STONE : material,
                type));
    }

    public void setBrushType(UUID playerId, String typeName) {
        BrushState prev = states.get(playerId);
        if (prev == null) {
            setBrush(playerId, 3, Material.STONE);
            prev = states.get(playerId);
        }
        BrushType type = switch (typeName == null ? "sphere" : typeName.toLowerCase(Locale.ROOT)) {
            case "cyl", "cylinder" -> BrushType.CYL;
            default -> BrushType.SPHERE;
        };
        states.put(playerId, new BrushState(prev.radius(), prev.material(), type));
    }

    public BrushState state(UUID playerId) {
        return states.get(playerId);
    }

    public CompletableFuture<Integer> apply(Player player, Location center) {
        BrushState state = states.get(player.getUniqueId());
        if (state == null) {
            return CompletableFuture.completedFuture(0);
        }
        return state.type() == BrushType.CYL ? applyCyl(player, center, state) : applySphere(player, center, state);
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
        EditSession session = new EditSession();
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rSq) {
                        continue;
                    }
                    Location loc = new Location(world, cx + x, cy + y, cz + z);
                    chain = chain.thenCompose(count -> paintOne(session, world, loc, state.material())
                            .thenApply(ok -> ok ? count + 1 : count));
                }
            }
        }
        return chain.thenApply(count -> {
            undo.push(player.getUniqueId(), session);
            return count;
        });
    }

    private CompletableFuture<Integer> applyCyl(Player player, Location center, BrushState state) {
        World world = center.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        EditSession session = new EditSession();
        int r = state.radius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int rSq = r * r;
        int height = Math.max(1, r);
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > rSq) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    Location loc = new Location(world, cx + x, cy + y, cz + z);
                    chain = chain.thenCompose(count -> paintOne(session, world, loc, state.material())
                            .thenApply(ok -> ok ? count + 1 : count));
                }
            }
        }
        return chain.thenApply(count -> {
            undo.push(player.getUniqueId(), session);
            return count;
        });
    }

    private CompletableFuture<Boolean> paintOne(EditSession session, World world, Location loc, Material material) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                String before = BlockCodec.encode(world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).setType(material, false);
                String after = BlockCodec.encode(world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                session.record(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), before, after);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        return done;
    }

    public record BrushState(int radius, Material material, BrushType type) {
        public BrushState(int radius, Material material) {
            this(radius, material, BrushType.SPHERE);
        }
    }
}
