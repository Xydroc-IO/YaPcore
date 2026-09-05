package com.yapcore.map;

import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
        config.applySpawnOrigin(world);
        List<String> layers = config.enabledLayers();
        for (int[] chunk : config.sampleChunks()) {
            int tileX = chunk[0];
            int tileZ = chunk[1];
            int chunkX = chunk[2];
            int chunkZ = chunk[3];
            YapSched.regionChunk(plugin, world, chunkX, chunkZ, () -> {
                try {
                    for (String layer : layers) {
                        int[][] rgb = sampleChunk(world, chunkX, chunkZ, layer);
                        writeTile(world.getName(), layer, 0, tileX, tileZ, rgb);
                    }
                    dirtyChunks.remove(world.getName() + "|" + chunkX + "|" + chunkZ);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed map tile " + world.getName() + " " + tileX + "_" + tileZ
                                    + " (chunk " + chunkX + "," + chunkZ + ")", e);
                }
            });
        }
        YapSched.async(plugin, () -> {
            try {
                for (String layer : layers) {
                    buildZoomPyramid(world.getName(), layer);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Map zoom pyramid failed for " + world.getName(), e);
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
            any = true;
            YapSched.regionChunk(plugin, world, chunkX, chunkZ, () -> {
                try {
                    for (String layer : layers) {
                        writeTile(world.getName(), layer, 0, tileX, tileZ,
                                sampleChunk(world, chunkX, chunkZ, layer));
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
        int radius = config.sampleChunkRadius();
        for (int zoom = 1; zoom <= MAX_ZOOM; zoom++) {
            int scale = 1 << zoom;
            int tiles = Math.max(1, (radius + scale - 1) / scale);
            for (int tx = 0; tx < tiles; tx++) {
                for (int tz = 0; tz < tiles; tz++) {
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
        int empty = colors.colorFor(Material.STONE).getRGB();
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

    private int[][] sampleChunk(World world, int chunkX, int chunkZ, String layer) {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        int baseX = chunkX * TILE_SIZE;
        int baseZ = chunkZ * TILE_SIZE;
        boolean cave = LAYER_CAVE.equals(normalizeLayer(layer));
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                Block block = sampleBlock(world, baseX + x, baseZ + z, cave);
                rgb[x][z] = tintedColor(world, block).getRGB();
            }
        }
        return rgb;
    }

    private Block sampleBlock(World world, int x, int z, boolean cave) {
        int worldMax = world.getMaxHeight() - 1;
        int minY = world.getMinHeight();
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Nether roof-aware: stay under bedrock ceiling when sampling surface-like tops.
            int netherCap = Math.min(126, Math.min(worldMax, config.maxHeight()));
            if (cave) {
                return highestSolid(world, x, z, Math.min(netherCap, config.caveMaxY()));
            }
            return highestSolid(world, x, z, netherCap);
        }
        int maxY = Math.min(worldMax, config.maxHeight());
        if (!cave) {
            return highestSolid(world, x, z, maxY);
        }
        Block surface = highestSolid(world, x, z, maxY);
        int caveCap = Math.min(surface.getY() - 1, config.caveMaxY());
        if (caveCap < minY) {
            return surface;
        }
        Block under = highestSolid(world, x, z, caveCap);
        // Prefer a solid with air above (open cave / underground void).
        for (int y = under.getY(); y >= minY; y--) {
            Block b = world.getBlockAt(x, y, z);
            Material type = b.getType();
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            Block above = world.getBlockAt(x, y + 1, z);
            if (above.getType().isAir() || !above.getType().isSolid()) {
                return b;
            }
        }
        return under;
    }

    private static Block highestSolid(World world, int x, int z, int maxY) {
        int minY = world.getMinHeight();
        int top = Math.max(minY, maxY);
        for (int y = top; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            return block;
        }
        return world.getBlockAt(x, minY, z);
    }

    private Color tintedColor(World world, Block block) {
        Color base = colors.colorFor(block.getType());
        if (!config.biomeTint()) {
            return base;
        }
        try {
            Biome biome = world.getBiome(block.getX(), block.getY(), block.getZ());
            return colors.tinted(base, biome, true);
        } catch (Throwable t) {
            return base;
        }
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
