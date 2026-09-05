package com.yapcore.map;

import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Folia-safe chunk mesh extraction: region-thread block reads, async encode/write.
 * Builds a dense RGB + model-kind buffer, applies mesh layer filters, greedy-meshes
 * (with non-cube models), writes LOD0–2 JSON + optional {@code .ymesh}, and can
 * follow players beyond the base sample window via a background queue.
 */
public final class ChunkMeshRenderer {

    private final MapConfig config;
    private final Path meshesRoot;
    private final MapBlockColors colors;
    private final Set<String> dirtyChunks = ConcurrentHashMap.newKeySet();
    /** Background expansion / re-center work — never blocks the region thread long. */
    private final ConcurrentLinkedQueue<String> backgroundQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> backgroundQueued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean backgroundDrainScheduled = new AtomicBoolean(false);
    private final AtomicInteger followOriginX = new AtomicInteger(Integer.MIN_VALUE);
    private final AtomicInteger followOriginZ = new AtomicInteger(Integer.MIN_VALUE);

    public ChunkMeshRenderer(MapConfig config, Path meshesRoot, MapBlockColors colors) {
        this.config = config;
        this.meshesRoot = meshesRoot;
        this.colors = colors == null ? new MapBlockColors() : colors;
    }

    public Path meshesRoot() {
        return meshesRoot;
    }

    public void markDirty(String world, int chunkX, int chunkZ) {
        dirtyChunks.add(world + "|" + chunkX + "|" + chunkZ);
    }

    public int dirtyCount() {
        return dirtyChunks.size() + backgroundQueue.size();
    }

    public void renderWorld(JavaPlugin plugin, World world) {
        if (!config.meshEnabled()) {
            return;
        }
        config.applySpawnOrigin(world);
        maybeFollowPlayers(plugin, world);
        for (int[] chunk : meshChunks()) {
            int chunkX = chunk[2];
            int chunkZ = chunk[3];
            scheduleExtract(plugin, world, chunkX, chunkZ, true);
        }
        YapSched.async(plugin, () -> writeManifestsSafe(plugin, world));
    }

    public void renderDirty(JavaPlugin plugin, World world) {
        if (!config.meshEnabled()) {
            return;
        }
        config.applySpawnOrigin(world);
        maybeFollowPlayers(plugin, world);
        String prefix = world.getName() + "|";
        boolean any = false;
        for (int[] chunk : meshChunks()) {
            int chunkX = chunk[2];
            int chunkZ = chunk[3];
            String key = prefix + chunkX + "|" + chunkZ;
            if (!dirtyChunks.contains(key)) {
                continue;
            }
            any = true;
            scheduleExtract(plugin, world, chunkX, chunkZ, false);
        }
        drainBackground(plugin, world);
        if (any) {
            YapSched.async(plugin, () -> writeManifestsSafe(plugin, world));
        }
    }

    /**
     * Re-center on average player position and/or expand dirty render toward players
     * near the sample window edge. New chunks are enqueued on the background queue.
     */
    void maybeFollowPlayers(JavaPlugin plugin, World world) {
        if (!config.meshFollowPlayers() && config.meshExtraRadius() <= 0) {
            return;
        }
        Collection<? extends Player> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        long sumX = 0;
        long sumZ = 0;
        int n = 0;
        for (Player p : players) {
            sumX += p.getLocation().getBlockX() >> 4;
            sumZ += p.getLocation().getBlockZ() >> 4;
            n++;
        }
        if (n == 0) {
            return;
        }
        int avgX = (int) (sumX / n);
        int avgZ = (int) (sumZ / n);
        int radius = config.meshSampleRadius();

        if (config.meshFollowPlayers()) {
            int curX = followOriginX.get();
            int curZ = followOriginZ.get();
            if (curX == Integer.MIN_VALUE) {
                followOriginX.set(config.originChunkX());
                followOriginZ.set(config.originChunkZ());
                curX = followOriginX.get();
                curZ = followOriginZ.get();
            }
            // Re-center when average player drifts ≥ half the window from origin center
            int centerX = curX + radius / 2;
            int centerZ = curZ + radius / 2;
            int drift = Math.max(Math.abs(avgX - centerX), Math.abs(avgZ - centerZ));
            if (drift >= Math.max(2, radius / 2)) {
                int newOx = avgX - radius / 2;
                int newOz = avgZ - radius / 2;
                followOriginX.set(newOx);
                followOriginZ.set(newOz);
                plugin.getLogger().info("YaPMap mesh follow re-center " + world.getName()
                        + " origin=" + newOx + "," + newOz);
                for (int[] chunk : meshChunks()) {
                    enqueueBackground(world.getName(), chunk[2], chunk[3]);
                }
            }
        }

        // Expand toward players near the edge of the current window
        int ox = effectiveOriginX();
        int oz = effectiveOriginZ();
        int edgePad = Math.max(1, config.meshExtraRadius());
        for (Player p : players) {
            int pcx = p.getLocation().getBlockX() >> 4;
            int pcz = p.getLocation().getBlockZ() >> 4;
            boolean nearEdge = pcx <= ox + edgePad || pcx >= ox + radius - 1 - edgePad
                    || pcz <= oz + edgePad || pcz >= oz + radius - 1 - edgePad;
            boolean outside = pcx < ox || pcx >= ox + radius || pcz < oz || pcz >= oz + radius;
            if (nearEdge || outside) {
                for (int dx = -edgePad; dx <= edgePad; dx++) {
                    for (int dz = -edgePad; dz <= edgePad; dz++) {
                        enqueueBackground(world.getName(), pcx + dx, pcz + dz);
                    }
                }
            }
        }
        drainBackground(plugin, world);
    }

    private void enqueueBackground(String world, int chunkX, int chunkZ) {
        String key = world + "|" + chunkX + "|" + chunkZ;
        if (backgroundQueued.add(key)) {
            backgroundQueue.offer(key);
        }
    }

    private void drainBackground(JavaPlugin plugin, World world) {
        if (!backgroundDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        // Process a few chunks per async turn — never blocks Folia beyond region reads.
        YapSched.async(plugin, () -> {
            int budget = 4;
            String prefix = world.getName() + "|";
            while (budget-- > 0) {
                String key = backgroundQueue.poll();
                if (key == null) {
                    break;
                }
                backgroundQueued.remove(key);
                if (!key.startsWith(prefix)) {
                    // Other world — requeue and stop this drain turn
                    backgroundQueue.offer(key);
                    backgroundQueued.add(key);
                    break;
                }
                String[] parts = key.split("\\|");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    int cx = Integer.parseInt(parts[1]);
                    int cz = Integer.parseInt(parts[2]);
                    scheduleExtract(plugin, world, cx, cz, true);
                } catch (NumberFormatException ignored) {
                }
            }
            boolean more = !backgroundQueue.isEmpty();
            if (!more) {
                writeManifestsSafe(plugin, world);
            }
            backgroundDrainScheduled.set(false);
            if (more) {
                drainBackground(plugin, world);
            }
        });
    }

    private int effectiveOriginX() {
        int f = followOriginX.get();
        return f == Integer.MIN_VALUE ? config.originChunkX() : f;
    }

    private int effectiveOriginZ() {
        int f = followOriginZ.get();
        return f == Integer.MIN_VALUE ? config.originChunkZ() : f;
    }

    private List<int[]> meshChunks() {
        if (config.meshFollowPlayers() || config.meshExtraRadius() > 0) {
            int ox = effectiveOriginX();
            int oz = effectiveOriginZ();
            return config.meshSampleChunks(ox, oz);
        }
        return config.sampleChunks();
    }

    private void scheduleExtract(JavaPlugin plugin, World world, int chunkX, int chunkZ,
                                 boolean clearDirtyAlways) {
        String key = world.getName() + "|" + chunkX + "|" + chunkZ;
        List<String> layers = config.enabledMeshLayers();
        boolean models = config.meshModels();
        boolean binary = config.meshBinary();
        int maxLod = config.meshMaxLod();
        YapSched.regionChunk(plugin, world, chunkX, chunkZ, () -> {
            try {
                ScanResult scan = scanDense(world, chunkX, chunkZ, models);
                int minY = world.getMinHeight();
                for (String layer : layers) {
                    int[][][] filtered = copyDense(scan.rgb);
                    byte[][][] kindFiltered = scan.kinds == null ? null : copyBytes(scan.kinds);
                    byte[][][] stateFiltered = scan.states == null ? null : copyBytes(scan.states);
                    MapLayerSampler.applyLayerFilter(filtered, minY, layer, config.caveMaxY());
                    // Clear model metadata where layer filter removed the solid
                    if (kindFiltered != null) {
                        syncModelsToRgb(filtered, kindFiltered, stateFiltered);
                    }
                    ChunkMeshData data = GreedyMesher.mesh(
                            chunkX, chunkZ, filtered, kindFiltered, stateFiltered, minY);
                    String layerFinal = layer;
                    YapSched.async(plugin, () -> {
                        try {
                            MeshEncoder.writeChunkLods(
                                    meshesRoot, world.getName(), layerFinal, data, maxLod, binary);
                            dirtyChunks.remove(key);
                        } catch (IOException e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Failed mesh write " + world.getName() + "/" + layerFinal
                                            + " " + chunkX + "_" + chunkZ, e);
                        }
                    });
                }
                if (clearDirtyAlways) {
                    dirtyChunks.remove(key);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed mesh extract " + world.getName() + " " + chunkX + "," + chunkZ, e);
            }
        });
    }

    /**
     * Package-private for tests: extract one layer after dense scan + filter + greedy mesh.
     */
    ChunkMeshData extractChunk(World world, int chunkX, int chunkZ) {
        return extractChunk(world, chunkX, chunkZ, MapLayerSampler.LAYER_FULL);
    }

    ChunkMeshData extractChunk(World world, int chunkX, int chunkZ, String layer) {
        ScanResult scan = scanDense(world, chunkX, chunkZ, config.meshModels());
        int minY = world.getMinHeight();
        MapLayerSampler.applyLayerFilter(scan.rgb, minY, layer, config.caveMaxY());
        if (scan.kinds != null) {
            syncModelsToRgb(scan.rgb, scan.kinds, scan.states);
        }
        return GreedyMesher.mesh(chunkX, chunkZ, scan.rgb, scan.kinds, scan.states, minY);
    }

    private ScanResult scanDense(World world, int chunkX, int chunkZ, boolean models) {
        int minY = world.getMinHeight();
        int maxY = columnMaxY(world);
        int baseX = chunkX * ChunkMeshExtractor.CHUNK_SIZE;
        int baseZ = chunkZ * ChunkMeshExtractor.CHUNK_SIZE;
        int ySpan = Math.max(1, maxY - minY + 1);
        int[][][] rgb = new int[ChunkMeshExtractor.CHUNK_SIZE][ySpan][ChunkMeshExtractor.CHUNK_SIZE];
        byte[][][] kinds = models
                ? new byte[ChunkMeshExtractor.CHUNK_SIZE][ySpan][ChunkMeshExtractor.CHUNK_SIZE]
                : null;
        byte[][][] states = models
                ? new byte[ChunkMeshExtractor.CHUNK_SIZE][ySpan][ChunkMeshExtractor.CHUNK_SIZE]
                : null;
        for (int lx = 0; lx < ChunkMeshExtractor.CHUNK_SIZE; lx++) {
            for (int yIndex = 0; yIndex < ySpan; yIndex++) {
                for (int lz = 0; lz < ChunkMeshExtractor.CHUNK_SIZE; lz++) {
                    rgb[lx][yIndex][lz] = -1;
                }
            }
        }
        boolean tint = config.biomeTint();
        for (int lx = 0; lx < ChunkMeshExtractor.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < ChunkMeshExtractor.CHUNK_SIZE; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir() || !type.isSolid()) {
                        continue;
                    }
                    Biome biome = null;
                    if (tint) {
                        try {
                            biome = world.getBiome(x, y, z);
                        } catch (Throwable ignored) {
                        }
                    }
                    int color = colors.tintedRgb(type, biome, tint);
                    int yi = y - minY;
                    rgb[lx][yi][lz] = color & 0xffffff;
                    if (models) {
                        kinds[lx][yi][lz] = BlockModelStates.kindOrdinal(type);
                        states[lx][yi][lz] = BlockModelStates.packedState(block);
                    }
                }
            }
        }
        return new ScanResult(rgb, kinds, states);
    }

    private static void syncModelsToRgb(int[][][] rgb, byte[][][] kinds, byte[][][] states) {
        for (int x = 0; x < rgb.length; x++) {
            if (rgb[x] == null) {
                continue;
            }
            for (int y = 0; y < rgb[x].length; y++) {
                if (rgb[x][y] == null) {
                    continue;
                }
                for (int z = 0; z < rgb[x][y].length; z++) {
                    if (rgb[x][y][z] < 0) {
                        if (kinds != null && kinds[x] != null && kinds[x][y] != null
                                && z < kinds[x][y].length) {
                            kinds[x][y][z] = 0;
                        }
                        if (states != null && states[x] != null && states[x][y] != null
                                && z < states[x][y].length) {
                            states[x][y][z] = 0;
                        }
                    }
                }
            }
        }
    }

    private static int[][][] copyDense(int[][][] src) {
        int[][][] copy = new int[src.length][][];
        for (int x = 0; x < src.length; x++) {
            if (src[x] == null) {
                continue;
            }
            copy[x] = new int[src[x].length][];
            for (int y = 0; y < src[x].length; y++) {
                if (src[x][y] == null) {
                    continue;
                }
                copy[x][y] = src[x][y].clone();
            }
        }
        return copy;
    }

    private static byte[][][] copyBytes(byte[][][] src) {
        byte[][][] copy = new byte[src.length][][];
        for (int x = 0; x < src.length; x++) {
            if (src[x] == null) {
                continue;
            }
            copy[x] = new byte[src[x].length][];
            for (int y = 0; y < src[x].length; y++) {
                if (src[x][y] == null) {
                    continue;
                }
                copy[x][y] = src[x][y].clone();
            }
        }
        return copy;
    }

    int columnMaxY(World world) {
        int env = 0;
        if (world.getEnvironment() == World.Environment.NETHER) {
            env = -1;
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            env = 1;
        }
        return ChunkMeshExtractor.columnMaxY(
                env, world.getMaxHeight(), world.getMinHeight(), config.meshMaxY());
    }

    private void writeManifestsSafe(JavaPlugin plugin, World world) {
        for (String layer : config.enabledMeshLayers()) {
            try {
                MeshEncoder.writeManifest(meshesRoot, world.getName(), layer,
                        effectiveOriginX(), effectiveOriginZ(), config.meshSampleRadius(),
                        config.meshMaxLod());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Mesh manifest failed for " + world.getName() + "/" + layer, e);
            }
        }
    }

    private record ScanResult(int[][][] rgb, byte[][][] kinds, byte[][][] states) {
    }
}
