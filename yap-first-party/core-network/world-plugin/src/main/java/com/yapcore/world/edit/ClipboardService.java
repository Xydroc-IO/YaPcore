package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.util.BlockCodec;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldEdit-class clipboard: copy / cut / paste / rotate / flip / stack / move.
 */
public final class ClipboardService {

    public record Clipboard(
            String world,
            List<Schematic.BlockEntry> blocks,
            int sizeX,
            int sizeY,
            int sizeZ,
            /** Player block pos relative to selection min at copy time. */
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
    }

    public static final int MAX_SLOTS = 3;

    private final JavaPlugin plugin;
    private final BlockBatch batch;
    private final Map<UUID, Clipboard[]> clipSlots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeSlot = new ConcurrentHashMap<>();
    private MaskEngine masks;
    private SelectionShape shapes;

    public ClipboardService(JavaPlugin plugin, UndoService undo) {
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

    public int slot(UUID playerId) {
        return activeSlot.getOrDefault(playerId, 0);
    }

    public void setSlot(UUID playerId, int slot) {
        activeSlot.put(playerId, Math.max(0, Math.min(MAX_SLOTS - 1, slot)));
    }

    public Clipboard clipboard(UUID playerId) {
        Clipboard[] slots = clipSlots.get(playerId);
        if (slots == null) {
            return null;
        }
        return slots[slot(playerId)];
    }

    public void putClipboard(UUID playerId, Clipboard clip) {
        Clipboard[] slots = clipSlots.computeIfAbsent(playerId, id -> new Clipboard[MAX_SLOTS]);
        slots[slot(playerId)] = clip;
    }

    public void clear(UUID playerId) {
        Clipboard[] slots = clipSlots.get(playerId);
        if (slots != null) {
            slots[slot(playerId)] = null;
        }
    }

    public void clearAll(UUID playerId) {
        clipSlots.remove(playerId);
        activeSlot.remove(playerId);
    }

    public CompletableFuture<Integer> copy(Player player, CuboidSelection sel, boolean cut) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        Location feet = player.getLocation();
        CompletableFuture<Integer> done = new CompletableFuture<>();
        YapSched.region(plugin, new Location(world, sel.minX(), sel.minY(), sel.minZ()), () -> {
            List<Schematic.BlockEntry> blocks = new ArrayList<>();
            List<BlockBatch.Planned> airOut = new ArrayList<>();
            UUID id = player.getUniqueId();
            for (int x = sel.minX(); x <= sel.maxX(); x++) {
                for (int y = sel.minY(); y <= sel.maxY(); y++) {
                    for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                        if (shapes != null && !shapes.contains(id, sel, x, y, z)) {
                            continue;
                        }
                        Block block = world.getBlockAt(x, y, z);
                        blocks.add(new Schematic.BlockEntry(
                                x - sel.minX(), y - sel.minY(), z - sel.minZ(),
                                BlockCodec.encode(block),
                                com.yapcore.world.util.TileCodec.capture(block)));
                        if (cut && !block.getType().isAir()) {
                            airOut.add(new BlockBatch.Planned(x, y, z, Material.AIR));
                        }
                    }
                }
            }
            int sizeX = sel.maxX() - sel.minX() + 1;
            int sizeY = sel.maxY() - sel.minY() + 1;
            int sizeZ = sel.maxZ() - sel.minZ() + 1;
            putClipboard(player.getUniqueId(), new Clipboard(
                    world.getName(),
                    blocks,
                    sizeX, sizeY, sizeZ,
                    feet.getBlockX() - sel.minX(),
                    feet.getBlockY() - sel.minY(),
                    feet.getBlockZ() - sel.minZ()));
            if (!cut || airOut.isEmpty()) {
                done.complete(blocks.size());
                return;
            }
            batch.apply(player, world, airOut).whenComplete((n, err) ->
                    done.complete(blocks.size()));
        });
        return done;
    }

    public CompletableFuture<Integer> paste(Player player, boolean ignoreAir) {
        Clipboard clip = clipboard(player.getUniqueId());
        if (clip == null) {
            return CompletableFuture.completedFuture(0);
        }
        World world = player.getWorld();
        Location feet = player.getLocation();
        int originX = feet.getBlockX() - clip.offsetX();
        int originY = feet.getBlockY() - clip.offsetY();
        int originZ = feet.getBlockZ() - clip.offsetZ();
        List<BlockBatch.Encoded> plans = new ArrayList<>();
        UUID id = player.getUniqueId();
        for (Schematic.BlockEntry entry : clip.blocks()) {
            if (ignoreAir && isAirEncoded(entry.encoded())) {
                continue;
            }
            int wx = originX + entry.dx();
            int wy = originY + entry.dy();
            int wz = originZ + entry.dz();
            if (masks != null && !masks.allows(id, world, wx, wy, wz)) {
                continue;
            }
            plans.add(new BlockBatch.Encoded(wx, wy, wz, entry.encoded(), entry.tileNbt()));
        }
        return batch.applyEncoded(player, world, plans);
    }

    public boolean rotateY(UUID playerId, int degrees) {
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return false;
        }
        int turns = ((degrees / 90) % 4 + 4) % 4;
        if (turns == 0) {
            return true;
        }
        List<Schematic.BlockEntry> rotated = new ArrayList<>();
        int minDx = Integer.MAX_VALUE;
        int minDz = Integer.MAX_VALUE;
        for (Schematic.BlockEntry e : clip.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
            }
            rotated.add(new Schematic.BlockEntry(dx, dy, dz, e.encoded(), e.tileNbt()));
            minDx = Math.min(minDx, dx);
            minDz = Math.min(minDz, dz);
        }
        List<Schematic.BlockEntry> normalized = new ArrayList<>();
        int maxDx = 0;
        int maxDy = 0;
        int maxDz = 0;
        for (Schematic.BlockEntry e : rotated) {
            int dx = e.dx() - minDx;
            int dz = e.dz() - minDz;
            normalized.add(new Schematic.BlockEntry(dx, e.dy(), dz, e.encoded(), e.tileNbt()));
            maxDx = Math.max(maxDx, dx);
            maxDy = Math.max(maxDy, e.dy());
            maxDz = Math.max(maxDz, dz);
        }
        // Rotate offset around Y as well
        int ox = clip.offsetX();
        int oz = clip.offsetZ();
        for (int t = 0; t < turns; t++) {
            int nox = oz;
            int noz = -ox;
            ox = nox;
            oz = noz;
        }
        ox -= minDx;
        oz -= minDz;
        putClipboard(playerId, new Clipboard(
                clip.world(), normalized, maxDx + 1, maxDy + 1, maxDz + 1, ox, clip.offsetY(), oz));
        return true;
    }

    public boolean flip(UUID playerId, char axis) {
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return false;
        }
        List<Schematic.BlockEntry> flipped = new ArrayList<>();
        for (Schematic.BlockEntry e : clip.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else if (axis == 'y' || axis == 'Y') {
                dy = clip.sizeY() - 1 - dy;
            } else {
                return false;
            }
            flipped.add(new Schematic.BlockEntry(dx, dy, dz, e.encoded(), e.tileNbt()));
        }
        int ox = clip.offsetX();
        int oy = clip.offsetY();
        int oz = clip.offsetZ();
        if (axis == 'x' || axis == 'X') {
            ox = clip.sizeX() - 1 - ox;
        } else if (axis == 'z' || axis == 'Z') {
            oz = clip.sizeZ() - 1 - oz;
        } else {
            oy = clip.sizeY() - 1 - oy;
        }
        putClipboard(playerId, new Clipboard(
                clip.world(), flipped, clip.sizeX(), clip.sizeY(), clip.sizeZ(), ox, oy, oz));
        return true;
    }

    public CompletableFuture<Integer> stack(Player player, CuboidSelection sel, Vector dir, int count) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null || count < 1) {
            return CompletableFuture.completedFuture(0);
        }
        int dx = dir.getBlockX() * (sel.maxX() - sel.minX() + 1);
        int dy = dir.getBlockY() * (sel.maxY() - sel.minY() + 1);
        int dz = dir.getBlockZ() * (sel.maxZ() - sel.minZ() + 1);
        CompletableFuture<Clipboard> captured = new CompletableFuture<>();
        YapSched.region(plugin, new Location(world, sel.minX(), sel.minY(), sel.minZ()), () -> {
            List<Schematic.BlockEntry> blocks = new ArrayList<>();
            for (int x = sel.minX(); x <= sel.maxX(); x++) {
                for (int y = sel.minY(); y <= sel.maxY(); y++) {
                    for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                        Block block = world.getBlockAt(x, y, z);
                        blocks.add(new Schematic.BlockEntry(
                                x - sel.minX(), y - sel.minY(), z - sel.minZ(),
                                BlockCodec.encode(block),
                                com.yapcore.world.util.TileCodec.capture(block)));
                    }
                }
            }
            captured.complete(new Clipboard(world.getName(), blocks,
                    sel.maxX() - sel.minX() + 1, sel.maxY() - sel.minY() + 1, sel.maxZ() - sel.minZ() + 1,
                    0, 0, 0));
        });
        return captured.thenCompose(clip -> {
            List<BlockBatch.Encoded> plans = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                int ox = sel.minX() + dx * i;
                int oy = sel.minY() + dy * i;
                int oz = sel.minZ() + dz * i;
                for (Schematic.BlockEntry e : clip.blocks()) {
                    plans.add(new BlockBatch.Encoded(ox + e.dx(), oy + e.dy(), oz + e.dz(), e.encoded(), e.tileNbt()));
                }
            }
            return batch.applyEncoded(player, world, plans);
        });
    }

    public CompletableFuture<Integer> move(Player player, CuboidSelection sel, Vector dir, int amount) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null || amount == 0) {
            return CompletableFuture.completedFuture(0);
        }
        int sx = dir.getBlockX() * amount;
        int sy = dir.getBlockY() * amount;
        int sz = dir.getBlockZ() * amount;
        return copy(player, sel, true).thenCompose(n -> {
            Clipboard clip = clipboard(player.getUniqueId());
            if (clip == null) {
                return CompletableFuture.completedFuture(0);
            }
            List<BlockBatch.Encoded> plans = new ArrayList<>();
            for (Schematic.BlockEntry e : clip.blocks()) {
                plans.add(new BlockBatch.Encoded(
                        sel.minX() + sx + e.dx(),
                        sel.minY() + sy + e.dy(),
                        sel.minZ() + sz + e.dz(),
                        e.encoded(),
                        e.tileNbt()));
            }
            return batch.applyEncoded(player, world, plans);
        });
    }

    /** Export active clipboard as a schematic (anchor 0,0,0; player offset preserved in comment via size). */
    public Schematic toSchematic(UUID playerId) {
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return null;
        }
        return new Schematic(clip.world(), 0, 0, 0, clip.blocks());
    }

    public void loadSchematic(UUID playerId, Schematic schem, int offsetX, int offsetY, int offsetZ) {
        if (schem == null) {
            return;
        }
        Schematic.Bounds b = schem.bounds();
        putClipboard(playerId, new Clipboard(
                schem.world(),
                schem.blocks(),
                Math.max(1, b.sizeX()),
                Math.max(1, b.sizeY()),
                Math.max(1, b.sizeZ()),
                offsetX, offsetY, offsetZ));
    }

    public String statusLine(UUID playerId) {
        int s = slot(playerId);
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return "slot " + s + " empty (0–" + (MAX_SLOTS - 1) + ")";
        }
        return "slot " + s + ": " + clip.blocks().size() + " blocks "
                + clip.sizeX() + "×" + clip.sizeY() + "×" + clip.sizeZ();
    }

    private static boolean isAirEncoded(String encoded) {
        return encoded == null || encoded.startsWith("AIR") || encoded.startsWith("minecraft:air")
                || encoded.startsWith("CAVE_AIR") || encoded.startsWith("VOID_AIR");
    }
}
