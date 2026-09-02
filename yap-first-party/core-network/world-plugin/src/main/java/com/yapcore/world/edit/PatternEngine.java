package com.yapcore.world.edit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * WorldEdit/FAWE-style patterns: materials, percentages, block-data, and specials
 * ({@code #existing}, {@code #solid}, {@code #clipboard}).
 */
public final class PatternEngine {

    @FunctionalInterface
    public interface Pattern {
        /** Resolve a block to place at world coords (may read existing block for specials). */
        Planned resolve(World world, int x, int y, int z, Function<Integer, String> clipboardAt);
    }

    public record Planned(Material material, BlockData data, String encoded) {
        public static Planned of(Material mat) {
            return new Planned(mat, null, null);
        }

        public static Planned of(BlockData data) {
            return new Planned(data.getMaterial(), data, null);
        }

        public static Planned encoded(String encoded) {
            return new Planned(null, null, encoded);
        }
    }

    private PatternEngine() {
    }

    public static Pattern parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return (w, x, y, z, clip) -> Planned.of(Material.STONE);
        }
        String pattern = raw.trim();
        String lower = pattern.toLowerCase(Locale.ROOT);
        if (lower.equals("#existing") || lower.equals("#keep")) {
            return (w, x, y, z, clip) -> {
                Block b = w.getBlockAt(x, y, z);
                return Planned.of(b.getBlockData().clone());
            };
        }
        if (lower.equals("#solid")) {
            return (w, x, y, z, clip) -> Planned.of(Material.STONE);
        }
        if (lower.equals("#clipboard") || lower.equals("#copy")) {
            return (w, x, y, z, clip) -> {
                if (clip == null) {
                    return Planned.of(Material.AIR);
                }
                // Relative index unused — clipboard brush supplies encoded via clip(0)
                String enc = clip.apply(0);
                if (enc == null) {
                    return Planned.of(Material.AIR);
                }
                return Planned.encoded(enc);
            };
        }
        List<WeightedPattern> parts = new ArrayList<>();
        for (String token : pattern.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int weight = 100;
            String body = t;
            int pct = t.indexOf('%');
            if (pct > 0) {
                try {
                    weight = Integer.parseInt(t.substring(0, pct).trim());
                    body = t.substring(pct + 1).trim();
                } catch (NumberFormatException ignored) {
                    weight = 100;
                }
            }
            Pattern single = parseSingle(body);
            parts.add(new WeightedPattern(Math.max(1, weight), single));
        }
        if (parts.isEmpty()) {
            return (w, x, y, z, clip) -> Planned.of(Material.STONE);
        }
        if (parts.size() == 1) {
            return parts.get(0).pattern();
        }
        int total = parts.stream().mapToInt(WeightedPattern::weight).sum();
        return (w, x, y, z, clip) -> {
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            int acc = 0;
            for (WeightedPattern wp : parts) {
                acc += wp.weight();
                if (roll < acc) {
                    return wp.pattern().resolve(w, x, y, z, clip);
                }
            }
            return parts.get(0).pattern().resolve(w, x, y, z, clip);
        };
    }

    private static Pattern parseSingle(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#")) {
            return parse(body);
        }
        int bracket = body.indexOf('[');
        if (bracket > 0 && body.endsWith("]")) {
            String name = body.substring(0, bracket);
            String states = body.substring(bracket);
            Material mat = Material.matchMaterial(name);
            if (mat == null || !mat.isBlock()) {
                return (w, x, y, z, clip) -> Planned.of(Material.STONE);
            }
            try {
                String asString = mat.getKey().toString() + states;
                BlockData data = Bukkit.createBlockData(asString);
                return (w, x, y, z, clip) -> Planned.of(data);
            } catch (IllegalArgumentException e) {
                return (w, x, y, z, clip) -> Planned.of(mat);
            }
        }
        Material mat = Material.matchMaterial(body);
        if (mat != null && mat.isBlock()) {
            return (w, x, y, z, clip) -> Planned.of(mat);
        }
        // Try full block data string
        try {
            BlockData data = Bukkit.createBlockData(body.contains(":") ? body : "minecraft:" + body.toLowerCase(Locale.ROOT));
            return (w, x, y, z, clip) -> Planned.of(data);
        } catch (IllegalArgumentException ignored) {
            return (w, x, y, z, clip) -> Planned.of(Material.STONE);
        }
    }

    /** Compatibility: pick a material from a simple pattern (no specials). */
    public static Material pickMaterial(String pattern) {
        Planned p = parse(pattern).resolve(null, 0, 0, 0, null);
        if (p.material() != null) {
            return p.material();
        }
        return Material.STONE;
    }

    public static BlockBatch.Planned toBatch(int x, int y, int z, Planned p) {
        if (p.encoded() != null) {
            // Encoded applied separately — return air placeholder shouldn't happen for material path
            Material mat = Material.AIR;
            return new BlockBatch.Planned(x, y, z, mat, null);
        }
        if (p.data() != null) {
            return new BlockBatch.Planned(x, y, z, p.data().getMaterial(), p.data());
        }
        return new BlockBatch.Planned(x, y, z, p.material() == null ? Material.STONE : p.material());
    }

    private record WeightedPattern(int weight, Pattern pattern) {
    }
}
