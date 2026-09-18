package com.yapcore.map;

import com.yapcore.sched.YapSched;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class TileRenderer {

    static final int TILE_SIZE = 16;
    static final int MAX_ZOOM = 3;
    public static final String LAYER_SURFACE = "surface";
    public static final String LAYER_CAVE = "cave";

    private final MapConfig config;
    private final Path tilesRoot;
    private final MapBlockColors colors;
    /** world|chunkX|chunkZ → dirty */
    private final Set<String> dirtyChunks = ConcurrentHashMap.newKeySet();

    public TileRenderer(MapConfig config, Path tilesRoot) {
        this(config, tilesRoot, new MapBlockColors());
    }

    public TileRenderer(MapConfig config, Path tilesRoot, MapBlockColors colors) {
        this.config = config;
        this.tilesRoot = tilesRoot;
        this.colors = colors == null ? new MapBlockColors() : colors;
    }

    public Path tilesRoot() {
        return tilesRoot;
    }

    public MapBlockColors colors() {
        return colors;
    }

    public void markDirty(String world, int chunkX, int chunkZ) {
        dirtyChunks.add(world + "|" + chunkX + "|" + chunkZ);
    }

    public int dirtyCount() {
        return dirtyChunks.size();
    }

    public void renderWorld(JavaPlugin plugin, World world) {
        renderWorld(plugin, world, null);
    }

    public void renderWorld(JavaPlugin plugin, World world, Runnable onComplete) {
        if (config.generatedExtent()) {
            config.bindSnapshot(MapExtent.scan(world));
        } else {
            config.applySpawnOrigin(world);
            config.bindSnapshot(null);
        }
        List<int[]> chunks = stripeByRegion(config.sampleChunks());
        List<String> layers = config.enabledLayers();
        plugin.getLogger().info("YaPMap " + world.getName() + " — " + chunks.size()
                + " chunks, grid " + config.gridChunksX() + "x" + config.gridChunksZ());
        if (plugin instanceof MapPlugin map) {
            map.refreshWebConfigAfterRender();
        }
        renderWave(plugin, world, layers, chunks, 0, onComplete);
    }

    private void renderWave(JavaPlugin plugin, World world, List<String> layers, List<int[]> chunks,
                            int index, Runnable onComplete) {
        if (index >= chunks.size()) {
            finishPyramid(plugin, world, layers, onComplete);
            return;
        }
        int end = Math.min(index + 16, chunks.size());
        AtomicInteger pending = new AtomicInteger(end - index);
        for (int i = index; i < end; i++) {
            int[] chunk = chunks.get(i);
            int tileX = chunk[0];
            int tileZ = chunk[1];
            int chunkX = chunk[2];
            int chunkZ = chunk[3];
            world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((loaded, err) -> {
                try {
                    if (loaded == null) {
                        return;
                    }
                    ChunkSnapshot snap = loaded.getChunkSnapshot(false, config.biomeTint(), false);
                    for (String layer : layers) {
                        writeTile(world.getName(), layer, 0, tileX, tileZ,
                                sampleChunk(snap, world, layer));
                    }
                    dirtyChunks.remove(world.getName() + "|" + chunkX + "|" + chunkZ);
                } catch (Throwable e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed map tile " + world.getName() + " " + tileX + "_" + tileZ, e);
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        YapSched.asyncLater(plugin, () ->
                                renderWave(plugin, world, layers, chunks, end, onComplete), 1L);
                    }
                }
            });
        }
    }

    /** Round-robin so one wave hits many region threads instead of one 32×32 region. */
    private static List<int[]> stripeByRegion(List<int[]> chunks) {
        LinkedHashMap<Long, List<int[]>> by = new LinkedHashMap<>();
        for (int[] chunk : chunks) {
            long key = (((long) chunk[2]) >> 5) << 32 | (((long) chunk[3]) >> 5 & 0xffffffffL);
            by.computeIfAbsent(key, ignored -> new ArrayList<>()).add(chunk);
        }
        List<int[]> out = new ArrayList<>(chunks.size());
        boolean more = true;
        while (more) {
            more = false;
            for (List<int[]> group : by.values()) {
                if (!group.isEmpty()) {
                    out.add(group.remove(0));
                    more = true;
                }
            }
        }
        return out;
    }

    private void finishPyramid(JavaPlugin plugin, World world, List<String> layers, Runnable onComplete) {
        YapSched.async(plugin, () -> {
            try {
                for (String layer : layers) {
                    buildZoomPyramid(world.getName(), layer);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Map zoom pyramid failed for " + world.getName(), e);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /** Re-render only dirty sample tiles for configured worlds. */
    public void renderDirty(JavaPlugin plugin, World world) {
        config.applySpawnOrigin(world);
        List<String> layers = config.enabledLayers();
        String prefix = world.getName() + "|";
        boolean any = false;
        for (int[] chunk : config.sampleChunks()) {
            int tileX = chunk[0];
            int tileZ = chunk[1];
            int chunkX = chunk[2];
            int chunkZ = chunk[3];
            String key = prefix + chunkX + "|" + chunkZ;
            if (!dirtyChunks.contains(key)) {
                continue;
            }
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }
            any = true;
            YapSched.regionChunk(plugin, world, chunkX, chunkZ, () -> {
                try {
                    ChunkSnapshot snap = ChunkSnapshots.captureIfLoaded(
                            world, chunkX, chunkZ, config.biomeTint());
                    if (snap == null) {
                        return;
                    }
                    for (String layer : layers) {
                        writeTile(world.getName(), layer, 0, tileX, tileZ,
                                sampleChunk(snap, world, layer));
                    }
                    dirtyChunks.remove(key);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.FINE, "dirty tile failed", e);
                }
            });
        }
        if (any) {
            YapSched.async(plugin, () -> {
                try {
                    for (String layer : layers) {
                        buildZoomPyramid(world.getName(), layer);
                    }
                } catch (IOException ignored) {
                }
            });
        }
    }

    public void buildZoomPyramid(String worldName, String layer) throws IOException {
        int width = config.gridChunksX();
        int height = config.gridChunksZ();
        int maxZoom = config.overviewZoom();
        for (int zoom = 1; zoom <= maxZoom; zoom++) {
            int scale = 1 << zoom;
            int tilesX = Math.max(1, (width + scale - 1) / scale);
            int tilesZ = Math.max(1, (height + scale - 1) / scale);
            for (int tx = 0; tx < tilesX; tx++) {
                for (int tz = 0; tz < tilesZ; tz++) {
                    writeTile(worldName, layer, zoom, tx, tz, compositeFromLower(worldName, layer, zoom, tx, tz));
                }
            }
        }
    }

    private int[][] compositeFromLower(String worldName, String layer, int zoom, int tileX, int tileZ)
            throws IOException {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        int half = TILE_SIZE / 2;
        for (int qx = 0; qx < 2; qx++) {
            for (int qz = 0; qz < 2; qz++) {
                Path src = tilePath(worldName, layer, zoom - 1, tileX * 2 + qx, tileZ * 2 + qz);
                int[][] srcRgb = readTileOrEmpty(src);
                for (int x = 0; x < half; x++) {
                    for (int z = 0; z < half; z++) {
                        int sx = x * 2;
                        int sz = z * 2;
                        int c00 = srcRgb[sx][sz];
                        int c10 = srcRgb[Math.min(TILE_SIZE - 1, sx + 1)][sz];
                        int c01 = srcRgb[sx][Math.min(TILE_SIZE - 1, sz + 1)];
                        int c11 = srcRgb[Math.min(TILE_SIZE - 1, sx + 1)][Math.min(TILE_SIZE - 1, sz + 1)];
                        rgb[qx * half + x][qz * half + z] = averageRgb(c00, c10, c01, c11);
                    }
                }
            }
        }
        return rgb;
    }

    private int[][] readTileOrEmpty(Path path) throws IOException {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        int empty = 0xff12161c;
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                rgb[x][z] = empty;
            }
        }
        if (!Files.isRegularFile(path)) {
            return rgb;
        }
        BufferedImage image = javax.imageio.ImageIO.read(path.toFile());
        if (image == null) {
            return rgb;
        }
        for (int x = 0; x < Math.min(TILE_SIZE, image.getWidth()); x++) {
            for (int z = 0; z < Math.min(TILE_SIZE, image.getHeight()); z++) {
                rgb[x][z] = image.getRGB(x, z);
            }
        }
        return rgb;
    }

    private static int averageRgb(int a, int b, int c, int d) {
        int r = (((a >> 16) & 0xff) + ((b >> 16) & 0xff) + ((c >> 16) & 0xff) + ((d >> 16) & 0xff)) / 4;
        int g = (((a >> 8) & 0xff) + ((b >> 8) & 0xff) + ((c >> 8) & 0xff) + ((d >> 8) & 0xff)) / 4;
        int bl = ((a & 0xff) + (b & 0xff) + (c & 0xff) + (d & 0xff)) / 4;
        return (0xff << 24) | (r << 16) | (g << 8) | bl;
    }

    public Path writeSampleTile(String worldName, int chunkX, int chunkZ) throws IOException {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                rgb[x][z] = colors.colorFor(Material.GRASS_BLOCK).getRGB();
            }
        }
        return writeTile(worldName, LAYER_SURFACE, 0, chunkX, chunkZ, rgb);
    }

    public Path writeTile(String worldName, String layer, int zoom, int tileX, int tileZ, int[][] rgb)
            throws IOException {
        Path out = tilePath(worldName, layer, zoom, tileX, tileZ);
        Files.createDirectories(out.getParent());
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                image.setRGB(x, z, rgb[x][z]);
            }
        }
        Files.write(out, pngBytes(image));
        return out;
    }

    /**
     * Tile layout: {@code {world}/{layer}/{zoom}/{x}_{z}.png}.
     * Surface also keeps a legacy alias at {@code {world}/{zoom}/…} for older bookmarks.
     */
    public Path tilePath(String worldName, String layer, int zoom, int tileX, int tileZ) {
        String safeLayer = normalizeLayer(layer);
        return tilesRoot.resolve(worldName + "/" + safeLayer + "/" + zoom + "/" + tileX + "_" + tileZ + ".png");
    }

    public static String normalizeLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return LAYER_SURFACE;
        }
        String n = layer.trim().toLowerCase(Locale.ROOT);
        if (LAYER_CAVE.equals(n)) {
            return LAYER_CAVE;
        }
        return LAYER_SURFACE;
    }

    private int[][] sampleChunk(ChunkSnapshot snap, World world, String layer) {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        boolean cave = LAYER_CAVE.equals(normalizeLayer(layer));
        int minY = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                int y = sampleY(snap, world, x, z, cave, minY, worldMax);
                Material type = ChunkSnapshots.blockType(snap, x, y, z);
                rgb[x][z] = tintedColor(type, ChunkSnapshots.biome(snap, x, y, z)).getRGB();
            }
        }
        return rgb;
    }

    private int sampleY(ChunkSnapshot snap, World world, int lx, int lz, boolean cave,
                        int minY, int worldMax) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            int netherCap = Math.min(126, Math.min(worldMax, config.maxHeight()));
            int cap = cave ? Math.min(netherCap, config.caveMaxY()) : netherCap;
            return ChunkSnapshots.highestSolidY(snap, lx, lz, minY, cap);
        }
        int maxY = Math.min(worldMax, config.maxHeight());
        int surfaceY;
        try {
            surfaceY = snap.getHighestBlockYAt(lx, lz);
        } catch (Throwable t) {
            surfaceY = ChunkSnapshots.highestSolidY(snap, lx, lz, minY, maxY);
        }
        if (surfaceY > maxY) {
            surfaceY = maxY;
        }
        if (!cave) {
            return Math.max(minY, surfaceY);
        }
        int caveCap = Math.min(surfaceY - 1, config.caveMaxY());
        if (caveCap < minY) {
            return surfaceY;
        }
        return ChunkSnapshots.highestSolidY(snap, lx, lz, minY, caveCap);
    }

    private Color tintedColor(Material type, Biome biome) {
        Color base = colors.colorFor(type);
        if (!config.biomeTint()) {
            return base;
        }
        return colors.tinted(base, biome, true);
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
