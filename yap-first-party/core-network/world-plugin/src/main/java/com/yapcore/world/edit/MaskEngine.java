package com.yapcore.world.edit;

import com.yapcore.world.CuboidSelection;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Per-player {@code //mask} / {@code //gmask} — FAWE-style filters applied before edits.
 */
public final class MaskEngine {

    @FunctionalInterface
    public interface Mask {
        boolean test(World world, int x, int y, int z);
    }

    private final ConcurrentHashMap<UUID, Mask> masks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Mask> gmasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CuboidSelection> regions = new ConcurrentHashMap<>();

    public void setMask(UUID playerId, String expression) {
        if (expression == null || expression.isBlank() || expression.equalsIgnoreCase("none")
                || expression.equalsIgnoreCase("clear") || expression.equalsIgnoreCase("-")) {
            masks.remove(playerId);
            return;
        }
        masks.put(playerId, parse(expression, playerId));
    }

    public void setGmask(UUID playerId, String expression) {
        if (expression == null || expression.isBlank() || expression.equalsIgnoreCase("none")
                || expression.equalsIgnoreCase("clear") || expression.equalsIgnoreCase("-")) {
            gmasks.remove(playerId);
            return;
        }
        gmasks.put(playerId, parse(expression, playerId));
    }

    public void clear(UUID playerId) {
        masks.remove(playerId);
        gmasks.remove(playerId);
    }

    public void bindRegion(UUID playerId, CuboidSelection sel) {
        if (sel == null) {
            regions.remove(playerId);
        } else {
            regions.put(playerId, sel);
        }
    }

    public String describeMask(UUID playerId) {
        return masks.containsKey(playerId) ? "set" : "none";
    }

    public String describeGmask(UUID playerId) {
        return gmasks.containsKey(playerId) ? "set" : "none";
    }

    public boolean allows(UUID playerId, World world, int x, int y, int z) {
        Mask g = gmasks.get(playerId);
        if (g != null && !g.test(world, x, y, z)) {
            return false;
        }
        Mask m = masks.get(playerId);
        return m == null || m.test(world, x, y, z);
    }

    public Mask combined(UUID playerId) {
        return (world, x, y, z) -> allows(playerId, world, x, y, z);
    }

    public Mask parse(String expression, UUID playerId) {
        String expr = expression.trim();
        boolean negate = false;
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1).trim();
        }
        Mask inner = parsePositive(expr, playerId);
        return negate ? (w, x, y, z) -> !inner.test(w, x, y, z) : inner;
    }

    /** Parse a replace-from mask used as {@code //replace <mask> <pattern>}. */
    public static Mask parseStatic(String expression) {
        MaskEngine tmp = new MaskEngine();
        return tmp.parse(expression, null);
    }

    private Mask parsePositive(String expr, UUID playerId) {
        String lower = expr.toLowerCase(Locale.ROOT);
        if (lower.equals("#air") || lower.equals("air")) {
            return (w, x, y, z) -> w.getBlockAt(x, y, z).getType().isAir();
        }
        if (lower.equals("#solid")) {
            return (w, x, y, z) -> {
                Material t = w.getBlockAt(x, y, z).getType();
                return t.isSolid() && !t.isAir();
            };
        }
        if (lower.equals("#existing")) {
            return (w, x, y, z) -> !w.getBlockAt(x, y, z).getType().isAir();
        }
        if (lower.equals("#region") || lower.equals("#selection") || lower.equals("#sel")) {
            return (w, x, y, z) -> {
                CuboidSelection sel = playerId == null ? null : regions.get(playerId);
                if (sel == null || w == null || !w.getName().equals(sel.world())) {
                    return true;
                }
                return x >= sel.minX() && x <= sel.maxX()
                        && y >= sel.minY() && y <= sel.maxY()
                        && z >= sel.minZ() && z <= sel.maxZ();
            };
        }
        if (expr.contains(",")) {
            List<Mask> any = new ArrayList<>();
            for (String part : expr.split(",")) {
                any.add(parsePositive(part.trim(), playerId));
            }
            return (w, x, y, z) -> {
                for (Mask m : any) {
                    if (m.test(w, x, y, z)) {
                        return true;
                    }
                }
                return false;
            };
        }
        Set<Material> mats = EnumSet.noneOf(Material.class);
        Material single = Material.matchMaterial(expr);
        if (single != null) {
            mats.add(single);
            return (w, x, y, z) -> mats.contains(w.getBlockAt(x, y, z).getType());
        }
        // Fallback: treat as always-true if unknown (avoid hard fail)
        return (w, x, y, z) -> true;
    }

    public static Predicate<Block> asBlockPredicate(Mask mask, World world) {
        return b -> mask.test(world, b.getX(), b.getY(), b.getZ());
    }
}
