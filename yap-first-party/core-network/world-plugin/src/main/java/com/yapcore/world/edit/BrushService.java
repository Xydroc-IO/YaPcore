package com.yapcore.world.edit;

import org.bukkit.Location;
import org.bukkit.Material;
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

    private final BlockBatch batch;
    private final Map<UUID, BrushState> states = new ConcurrentHashMap<>();
    private final BrushApplicator applicator;
    private MaskEngine masks;
    private PlayerEditState editState;
    private ClipboardService clipboard;
    private int maxRadius = 16;

    public BrushService(JavaPlugin plugin, UndoService undo) {
        this.batch = new BlockBatch(plugin, undo);
        this.applicator = new BrushApplicator(plugin, batch, () -> masks, () -> clipboard);
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
            case CYL -> applicator.applyCyl(player, center, state);
            case SMOOTH -> applicator.applySmooth(player, center, state);
            case GRAVITY -> applicator.applyGravity(player, center, state);
            case CLIPBOARD -> applicator.applyClipboard(player, center, state);
            case BUTCHER -> applicator.applyButcher(player, center, state);
            case ERODE -> applicator.applyErode(player, center, state);
            case RAISE -> applicator.applyRaiseLower(player, center, state, 1);
            case LOWER -> applicator.applyRaiseLower(player, center, state, -1);
            case MELT -> applicator.applyMelt(player, center, state);
            case FILL -> applicator.applyFill(player, center, state);
            case FOREST -> applicator.applyForest(player, center, state);
            default -> applicator.applySphere(player, center, state);
        };
    }

    public CompletableFuture<Integer> applySphere(Player player, Location center) {
        BrushState state = states.get(player.getUniqueId());
        if (state == null) {
            return CompletableFuture.completedFuture(0);
        }
        return applicator.applySphere(player, center, state);
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

    public record BrushState(int radius, Material material, BrushType type, String pattern) {
        public BrushState(int radius, Material material) {
            this(radius, material, BrushType.SPHERE, material == null ? "stone" : material.name().toLowerCase(Locale.ROOT));
        }

        public BrushState(int radius, Material material, BrushType type) {
            this(radius, material, type, material == null ? "stone" : material.name().toLowerCase(Locale.ROOT));
        }
    }
}
