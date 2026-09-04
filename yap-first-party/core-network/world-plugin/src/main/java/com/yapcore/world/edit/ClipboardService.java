package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * WorldEdit-class clipboard: copy / cut / paste / rotate / flip / stack / move.
 * Phase 5: entities + biomes on clipboard, paste flags {@code -a -e -b -o -s}.
 */
public final class ClipboardService {

    public record BiomeEntry(int dx, int dy, int dz, String biome) {
    }

    public record Clipboard(
            String world,
            List<Schematic.BlockEntry> blocks,
            List<Schematic.EntityEntry> entities,
            List<BiomeEntry> biomes,
            int sizeX,
            int sizeY,
            int sizeZ,
            /** Player block pos relative to selection min at copy time. */
            int offsetX,
            int offsetY,
            int offsetZ,
            /** Absolute selection min at copy (for {@code -o}). */
            int originX,
            int originY,
            int originZ
    ) {
        public Clipboard(String world, List<Schematic.BlockEntry> blocks,
                         int sizeX, int sizeY, int sizeZ,
                         int offsetX, int offsetY, int offsetZ) {
            this(world, blocks, List.of(), List.of(), sizeX, sizeY, sizeZ,
                    offsetX, offsetY, offsetZ, 0, 0, 0);
        }
    }

    public record PasteOptions(
            boolean ignoreAir,
            boolean entities,
            boolean biomes,
            boolean atOrigin,
            boolean selectAfter
    ) {
        public static PasteOptions parse(String[] args) {
            boolean ignoreAir = false;
            boolean entities = false;
            boolean biomes = false;
            boolean atOrigin = false;
            boolean selectAfter = false;
            if (args == null) {
                return new PasteOptions(false, false, false, false, false);
            }
            for (String a : args) {
                if (a == null || a.isBlank()) {
                    continue;
                }
                String t = a.toLowerCase(Locale.ROOT);
                if ("-a".equals(t) || "air".equals(t)) {
                    ignoreAir = true;
                } else if ("-e".equals(t) || "entities".equals(t) || "entity".equals(t)) {
                    entities = true;
                } else if ("-b".equals(t) || "biomes".equals(t) || "biome".equals(t)) {
                    biomes = true;
                } else if ("-o".equals(t) || "origin".equals(t)) {
                    atOrigin = true;
                } else if ("-s".equals(t) || "select".equals(t)) {
                    selectAfter = true;
                } else if (t.startsWith("-") && t.length() > 2) {
                    // Combined flags e.g. -ae, -aes
                    for (int i = 1; i < t.length(); i++) {
                        char c = t.charAt(i);
                        if (c == 'a') {
                            ignoreAir = true;
                        } else if (c == 'e') {
                            entities = true;
                        } else if (c == 'b') {
                            biomes = true;
                        } else if (c == 'o') {
                            atOrigin = true;
                        } else if (c == 's') {
                            selectAfter = true;
                        }
                    }
                }
            }
            return new PasteOptions(ignoreAir, entities, biomes, atOrigin, selectAfter);
        }
    }

    public static final int MAX_SLOTS = 3;

    private final JavaPlugin plugin;
    private final BlockBatch batch;
    private final Map<UUID, Clipboard[]> clipSlots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeSlot = new ConcurrentHashMap<>();
    private MaskEngine masks;
    private SelectionShape shapes;
    private PlayerEditState editState;
    private BiConsumer<Player, String[]> selectHook;

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
        this.editState = state;
        batch.setEditState(state);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    public void setLargePasteTuning(int largeBlocks, int parallelLarge, boolean autoFast) {
        batch.setLargePasteBlocks(largeBlocks);
        batch.setParallelChunksLarge(parallelLarge);
        batch.setAutoFastLarge(autoFast);
    }

    public void setProgressListener(BlockBatch.ProgressListener listener) {
        batch.setProgressListener(listener);
    }

    public boolean isLargePaste(int blocks) {
        return batch.isLarge(blocks);
    }

    public void setProgressHook(BiConsumer<UUID, Integer> progressHook) {
        batch.setProgressHook(progressHook);
    }

    /** Called after paste with {@code -s}: world, minX,minY,minZ,maxX,maxY,maxZ. */
    public void setSelectHook(BiConsumer<Player, String[]> selectHook) {
        this.selectHook = selectHook;
    }

    public BlockBatch batch() {
        return batch;
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
            List<BiomeEntry> biomes = new ArrayList<>();
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
                        if (y == sel.minY() || (y - sel.minY()) % 4 == 0) {
                            biomes.add(new BiomeEntry(
                                    x - sel.minX(), y - sel.minY(), z - sel.minZ(),
                                    world.getBiome(x, y, z).getKey().getKey()));
                        }
                        if (cut && !block.getType().isAir()) {
                            airOut.add(new BlockBatch.Planned(x, y, z, Material.AIR));
                        }
                    }
                }
            }
            List<Schematic.EntityEntry> entities = captureEntities(world, sel);
            if (cut) {
                for (Schematic.EntityEntry e : entities) {
                    removeEntityAt(world, sel.minX() + e.dx(), sel.minY() + e.dy(), sel.minZ() + e.dz(), e.type());
                }
            }
            int sizeX = sel.maxX() - sel.minX() + 1;
            int sizeY = sel.maxY() - sel.minY() + 1;
            int sizeZ = sel.maxZ() - sel.minZ() + 1;
            putClipboard(player.getUniqueId(), new Clipboard(
                    world.getName(),
                    blocks,
                    entities,
                    biomes,
                    sizeX, sizeY, sizeZ,
                    feet.getBlockX() - sel.minX(),
                    feet.getBlockY() - sel.minY(),
                    feet.getBlockZ() - sel.minZ(),
                    sel.minX(), sel.minY(), sel.minZ()));
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
        return paste(player, new PasteOptions(ignoreAir, false, false, false, false));
    }

    public CompletableFuture<Integer> paste(Player player, PasteOptions opts) {
        Clipboard clip = clipboard(player.getUniqueId());
        if (clip == null) {
            return CompletableFuture.completedFuture(0);
        }
        World world = opts.atOrigin()
                ? (Bukkit.getWorld(clip.world()) != null ? Bukkit.getWorld(clip.world()) : player.getWorld())
                : player.getWorld();
        Location feet = player.getLocation();
        final int originX;
        final int originY;
        final int originZ;
        if (opts.atOrigin()) {
            originX = clip.originX();
            originY = clip.originY();
            originZ = clip.originZ();
        } else {
            originX = feet.getBlockX() - clip.offsetX();
            originY = feet.getBlockY() - clip.offsetY();
            originZ = feet.getBlockZ() - clip.offsetZ();
        }
        List<BlockBatch.Encoded> plans = new ArrayList<>();
        UUID id = player.getUniqueId();
        for (Schematic.BlockEntry entry : clip.blocks()) {
            if (opts.ignoreAir() && isAirEncoded(entry.encoded())) {
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
        return batch.applyEncoded(player, world, plans).thenCompose(n -> {
            if (editState != null) {
                editState.setLastEditBounds(player.getUniqueId(), world.getName(),
                        originX, originY, originZ,
                        originX + clip.sizeX() - 1,
                        originY + clip.sizeY() - 1,
                        originZ + clip.sizeZ() - 1);
            }
            CompletableFuture<Integer> after = CompletableFuture.completedFuture(n);
            if (opts.biomes() && !clip.biomes().isEmpty()) {
                after = after.thenCompose(count -> applyBiomes(world, clip.biomes(), originX, originY, originZ)
                        .thenApply(b -> count));
            }
            if (opts.entities() && !clip.entities().isEmpty()) {
                after = after.thenCompose(count -> spawnEntities(world, clip.entities(), originX, originY, originZ)
                        .thenApply(e -> count + e));
            }
            return after.thenApply(count -> {
                if (opts.selectAfter() && selectHook != null) {
                    selectHook.accept(player, new String[]{
                            world.getName(),
                            String.valueOf(originX), String.valueOf(originY), String.valueOf(originZ),
                            String.valueOf(originX + clip.sizeX() - 1),
                            String.valueOf(originY + clip.sizeY() - 1),
                            String.valueOf(originZ + clip.sizeZ() - 1)
                    });
                }
                return count;
            });
        });
    }

    private CompletableFuture<Integer> applyBiomes(World world, List<BiomeEntry> biomes,
                                                   int ox, int oy, int oz) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        if (biomes.isEmpty()) {
            done.complete(0);
            return done;
        }
        BiomeEntry first = biomes.get(0);
        YapSched.region(plugin, new Location(world, ox + first.dx(), oy + first.dy(), oz + first.dz()), () -> {
            int n = 0;
            for (BiomeEntry b : biomes) {
                Biome biome = matchBiome(b.biome());
                if (biome == null) {
                    continue;
                }
                world.setBiome(ox + b.dx(), oy + b.dy(), oz + b.dz(), biome);
                n++;
            }
            done.complete(n);
        });
        return done;
    }

    private CompletableFuture<Integer> spawnEntities(World world, List<Schematic.EntityEntry> entities,
                                                     int ox, int oy, int oz) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        if (entities.isEmpty()) {
            done.complete(0);
            return done;
        }
        Schematic.EntityEntry first = entities.get(0);
        YapSched.region(plugin, new Location(world, ox + first.dx(), oy + first.dy(), oz + first.dz()), () -> {
            int n = 0;
            for (Schematic.EntityEntry e : entities) {
                EntityType type;
                try {
                    type = EntityType.valueOf(e.type());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!type.isSpawnable()) {
                    continue;
                }
                Location loc = new Location(world, ox + e.dx() + 0.5, oy + e.dy(), oz + e.dz() + 0.5,
                        e.yaw(), e.pitch());
                Entity spawned = world.spawnEntity(loc, type);
                if (spawned instanceof LivingEntity living && e.nbt() != null && e.nbt().startsWith("custom=")
                        && e.nbt().length() > 7) {
                    living.setCustomName(e.nbt().substring(7));
                    living.setCustomNameVisible(true);
                }
                n++;
            }
            done.complete(n);
        });
        return done;
    }

    private static List<Schematic.EntityEntry> captureEntities(World world, CuboidSelection sel) {
        List<Schematic.EntityEntry> entities = new ArrayList<>();
        Location min = new Location(world, sel.minX(), sel.minY(), sel.minZ());
        Location max = new Location(world, sel.maxX() + 1, sel.maxY() + 1, sel.maxZ() + 1);
        Collection<Entity> nearby = world.getNearbyEntities(
                min.toVector().getMidpoint(max.toVector()).toLocation(world),
                (sel.maxX() - sel.minX()) / 2.0 + 1,
                (sel.maxY() - sel.minY()) / 2.0 + 1,
                (sel.maxZ() - sel.minZ()) / 2.0 + 1);
        for (Entity e : nearby) {
            if (e instanceof Player) {
                continue;
            }
            Location loc = e.getLocation();
            if (loc.getBlockX() < sel.minX() || loc.getBlockX() > sel.maxX()
                    || loc.getBlockY() < sel.minY() || loc.getBlockY() > sel.maxY()
                    || loc.getBlockZ() < sel.minZ() || loc.getBlockZ() > sel.maxZ()) {
                continue;
            }
            String nbt = e instanceof LivingEntity living
                    ? "custom=" + (living.getCustomName() == null ? "" : living.getCustomName())
                    : "";
            entities.add(new Schematic.EntityEntry(
                    loc.getBlockX() - sel.minX(),
                    loc.getBlockY() - sel.minY(),
                    loc.getBlockZ() - sel.minZ(),
                    e.getType().name(),
                    loc.getYaw(),
                    loc.getPitch(),
                    nbt));
        }
        return entities;
    }

    private static void removeEntityAt(World world, int x, int y, int z, String typeName) {
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Entity e : world.getNearbyEntities(new Location(world, x + 0.5, y, z + 0.5), 0.6, 0.6, 0.6)) {
            if (e instanceof Player) {
                continue;
            }
            if (e.getType() == type) {
                e.remove();
                return;
            }
        }
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
        List<Schematic.EntityEntry> ents = rotateEntities(clip.entities(), turns, minDx, minDz);
        List<BiomeEntry> bios = rotateBiomes(clip.biomes(), turns, minDx, minDz);
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
                clip.world(), normalized, ents, bios,
                maxDx + 1, maxDy + 1, maxDz + 1, ox, clip.offsetY(), oz,
                clip.originX(), clip.originY(), clip.originZ()));
        return true;
    }

    private static List<Schematic.EntityEntry> rotateEntities(List<Schematic.EntityEntry> entities,
                                                              int turns, int minDx, int minDz) {
        List<Schematic.EntityEntry> out = new ArrayList<>();
        for (Schematic.EntityEntry e : entities) {
            int dx = e.dx();
            int dz = e.dz();
            float yaw = e.yaw();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
                yaw += 90f;
            }
            out.add(new Schematic.EntityEntry(dx - minDx, e.dy(), dz - minDz, e.type(), yaw, e.pitch(), e.nbt()));
        }
        return out;
    }

    private static List<BiomeEntry> rotateBiomes(List<BiomeEntry> biomes, int turns, int minDx, int minDz) {
        List<BiomeEntry> out = new ArrayList<>();
        for (BiomeEntry b : biomes) {
            int dx = b.dx();
            int dz = b.dz();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
            }
            out.add(new BiomeEntry(dx - minDx, b.dy(), dz - minDz, b.biome()));
        }
        return out;
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
        List<Schematic.EntityEntry> ents = new ArrayList<>();
        for (Schematic.EntityEntry e : clip.entities()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else {
                dy = clip.sizeY() - 1 - dy;
            }
            ents.add(new Schematic.EntityEntry(dx, dy, dz, e.type(), e.yaw(), e.pitch(), e.nbt()));
        }
        List<BiomeEntry> bios = new ArrayList<>();
        for (BiomeEntry b : clip.biomes()) {
            int dx = b.dx();
            int dy = b.dy();
            int dz = b.dz();
            if (axis == 'x' || axis == 'X') {
                dx = clip.sizeX() - 1 - dx;
            } else if (axis == 'z' || axis == 'Z') {
                dz = clip.sizeZ() - 1 - dz;
            } else {
                dy = clip.sizeY() - 1 - dy;
            }
            bios.add(new BiomeEntry(dx, dy, dz, b.biome()));
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
                clip.world(), flipped, ents, bios,
                clip.sizeX(), clip.sizeY(), clip.sizeZ(), ox, oy, oz,
                clip.originX(), clip.originY(), clip.originZ()));
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
            captured.complete(new Clipboard(world.getName(), blocks, List.of(), List.of(),
                    sel.maxX() - sel.minX() + 1, sel.maxY() - sel.minY() + 1, sel.maxZ() - sel.minZ() + 1,
                    0, 0, 0, sel.minX(), sel.minY(), sel.minZ()));
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
            return batch.applyEncoded(player, world, plans).thenCompose(blocks -> {
                if (clip.entities().isEmpty()) {
                    return CompletableFuture.completedFuture(blocks);
                }
                return spawnEntities(world, clip.entities(),
                        sel.minX() + sx, sel.minY() + sy, sel.minZ() + sz)
                        .thenApply(e -> blocks);
            });
        });
    }

    public Schematic toSchematic(UUID playerId) {
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return null;
        }
        return new Schematic(clip.world(), 0, 0, 0, clip.blocks(), clip.entities());
    }

    public void loadSchematic(UUID playerId, Schematic schem, int offsetX, int offsetY, int offsetZ) {
        if (schem == null) {
            return;
        }
        Schematic.Bounds b = schem.bounds();
        putClipboard(playerId, new Clipboard(
                schem.world(),
                schem.blocks(),
                schem.entities(),
                List.of(),
                Math.max(1, b.sizeX()),
                Math.max(1, b.sizeY()),
                Math.max(1, b.sizeZ()),
                offsetX, offsetY, offsetZ,
                schem.anchorX(), schem.anchorY(), schem.anchorZ()));
    }

    public String statusLine(UUID playerId) {
        int s = slot(playerId);
        Clipboard clip = clipboard(playerId);
        if (clip == null) {
            return "slot " + s + " empty (0–" + (MAX_SLOTS - 1) + ")";
        }
        return "slot " + s + ": " + clip.blocks().size() + " blocks, "
                + clip.entities().size() + " ents "
                + clip.sizeX() + "×" + clip.sizeY() + "×" + clip.sizeZ();
    }

    private static boolean isAirEncoded(String encoded) {
        return encoded == null || encoded.startsWith("AIR") || encoded.startsWith("minecraft:air")
                || encoded.startsWith("CAVE_AIR") || encoded.startsWith("VOID_AIR");
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
