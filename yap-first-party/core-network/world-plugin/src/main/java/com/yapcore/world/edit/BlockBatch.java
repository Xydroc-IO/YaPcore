package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Chunk-batched block writes (Folia-safe) with undo recording.
 */
public final class BlockBatch {

    public record Planned(int x, int y, int z, Material material, BlockData data) {
        public Planned(int x, int y, int z, Material material) {
            this(x, y, z, material, null);
        }
    }

    private final JavaPlugin plugin;
    private final UndoService undo;

    public BlockBatch(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.undo = undo;
    }

    public CompletableFuture<Integer> apply(Player player, World world, List<Planned> planned) {
        if (planned.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Map<Long, List<Planned>> byChunk = new HashMap<>();
        for (Planned p : planned) {
            long key = (((long) (p.x() >> 4)) << 32) ^ (p.z() >> 4);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        EditSession session = new EditSession();
        AtomicInteger changed = new AtomicInteger();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (List<Planned> chunkPlans : byChunk.values()) {
            Planned first = chunkPlans.get(0);
            Location anchor = new Location(world, first.x(), first.y(), first.z());
            chain = chain.thenCompose(v -> applyChunk(session, world, anchor, chunkPlans, changed));
        }
        return chain.thenApply(v -> {
            undo.push(player.getUniqueId(), session);
            return changed.get();
        });
    }

    public CompletableFuture<Integer> applyEncoded(Player player, World world, List<Encoded> planned) {
        if (planned.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Map<Long, List<Encoded>> byChunk = new HashMap<>();
        for (Encoded p : planned) {
            long key = (((long) (p.x() >> 4)) << 32) ^ (p.z() >> 4);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        EditSession session = new EditSession();
        AtomicInteger changed = new AtomicInteger();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (List<Encoded> chunkPlans : byChunk.values()) {
            Encoded first = chunkPlans.get(0);
            Location anchor = new Location(world, first.x(), first.y(), first.z());
            chain = chain.thenCompose(v -> applyEncodedChunk(session, world, anchor, chunkPlans, changed));
        }
        return chain.thenApply(v -> {
            undo.push(player.getUniqueId(), session);
            return changed.get();
        });
    }

    public record Encoded(int x, int y, int z, String encoded) {
    }

    private CompletableFuture<Void> applyChunk(EditSession session, World world, Location anchor,
                                               List<Planned> plans, AtomicInteger changed) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, anchor, () -> {
            try {
                for (Planned p : plans) {
                    Block block = world.getBlockAt(p.x(), p.y(), p.z());
                    String before = BlockCodec.encode(block);
                    if (p.data() != null) {
                        block.setBlockData(p.data(), false);
                    } else {
                        block.setType(p.material(), false);
                    }
                    String after = BlockCodec.encode(block);
                    if (!before.equals(after)) {
                        session.record(world.getName(), p.x(), p.y(), p.z(), before, after);
                        changed.incrementAndGet();
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        return done;
    }

    private CompletableFuture<Void> applyEncodedChunk(EditSession session, World world, Location anchor,
                                                      List<Encoded> plans, AtomicInteger changed) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, anchor, () -> {
            try {
                for (Encoded p : plans) {
                    Block block = world.getBlockAt(p.x(), p.y(), p.z());
                    String before = BlockCodec.encode(block);
                    BlockCodec.apply(block, p.encoded());
                    String after = BlockCodec.encode(block);
                    if (!before.equals(after)) {
                        session.record(world.getName(), p.x(), p.y(), p.z(), before, after);
                        changed.incrementAndGet();
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        return done;
    }

    /** Parse {@code stone} or {@code 50%stone,50%dirt}. */
    public static Material pickPattern(String pattern) {
        List<Weighted> weights = parsePattern(pattern);
        if (weights.isEmpty()) {
            return Material.STONE;
        }
        int total = weights.stream().mapToInt(Weighted::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int acc = 0;
        for (Weighted w : weights) {
            acc += w.weight();
            if (roll < acc) {
                return w.material();
            }
        }
        return weights.get(0).material();
    }

    public static List<Weighted> parsePattern(String pattern) {
        List<Weighted> out = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            return out;
        }
        for (String part : pattern.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int weight = 100;
            String name = token;
            int pct = token.indexOf('%');
            if (pct > 0) {
                try {
                    weight = Integer.parseInt(token.substring(0, pct).trim());
                    name = token.substring(pct + 1).trim();
                } catch (NumberFormatException ignored) {
                    weight = 100;
                }
            }
            Material mat = Material.matchMaterial(name);
            if (mat != null && mat.isBlock()) {
                out.add(new Weighted(Math.max(1, weight), mat));
            }
        }
        return out;
    }

    public record Weighted(int weight, Material material) {
    }

    public static void forEachBlock(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                    TriConsumer consumer) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    consumer.accept(x, y, z);
                }
            }
        }
    }

    @FunctionalInterface
    public interface TriConsumer {
        void accept(int x, int y, int z);
    }
}
