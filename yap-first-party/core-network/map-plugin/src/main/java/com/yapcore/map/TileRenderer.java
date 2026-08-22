package com.yapcore.map;

import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

public final class TileRenderer {

    private static final int TILE_SIZE = 16;
    private static final int ZOOM = 0;

    private final MapConfig config;
    private final Path tilesRoot;
    private final Map<Material, Color> colors = new EnumMap<>(Material.class);

    public TileRenderer(MapConfig config, Path tilesRoot) {
        this.config = config;
        this.tilesRoot = tilesRoot;
        seedColors();
    }

    public Path tilesRoot() {
        return tilesRoot;
    }

    public void renderWorld(JavaPlugin plugin, World world) {
        for (int[] chunk : config.sampleChunks()) {
            int chunkX = chunk[0];
            int chunkZ = chunk[1];
            YapSched.regionChunk(plugin, world, chunkX, chunkZ, () -> {
                try {
                    writeTile(world.getName(), chunkX, chunkZ, sampleChunk(world, chunkX, chunkZ));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed map tile " + world.getName() + " " + chunkX + "_" + chunkZ, e);
                }
            });
        }
    }

    public Path writeSampleTile(String worldName, int chunkX, int chunkZ) throws IOException {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                rgb[x][z] = colorFor(Material.GRASS_BLOCK).getRGB();
            }
        }
        return writeTile(worldName, chunkX, chunkZ, rgb);
    }

    public Path writeTile(String worldName, int chunkX, int chunkZ, int[][] rgb) throws IOException {
        Path out = tilePath(worldName, chunkX, chunkZ);
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

    public Path tilePath(String worldName, int chunkX, int chunkZ) {
        return tilesRoot.resolve(worldName + "/" + ZOOM + "/" + chunkX + "_" + chunkZ + ".png");
    }

    private int[][] sampleChunk(World world, int chunkX, int chunkZ) {
        int[][] rgb = new int[TILE_SIZE][TILE_SIZE];
        int baseX = chunkX * TILE_SIZE;
        int baseZ = chunkZ * TILE_SIZE;
        int maxY = Math.min(world.getMaxHeight() - 1, config.maxHeight());
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                Block top = highestSolid(world, baseX + x, baseZ + z, maxY);
                rgb[x][z] = colorFor(top.getType()).getRGB();
            }
        }
        return rgb;
    }

    private static Block highestSolid(World world, int x, int z, int maxY) {
        for (int y = maxY; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            return block;
        }
        return world.getBlockAt(x, world.getMinHeight(), z);
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private Color colorFor(Material material) {
        return colors.getOrDefault(material, colors.get(Material.STONE));
    }

    private void seedColors() {
        put(Material.GRASS_BLOCK, 95, 159, 53);
        put(Material.DIRT, 134, 96, 67);
        put(Material.STONE, 125, 125, 125);
        put(Material.WATER, 63, 118, 228);
        put(Material.SAND, 219, 211, 160);
        put(Material.SNOW_BLOCK, 240, 240, 240);
        put(Material.ICE, 160, 200, 255);
        put(Material.OAK_LOG, 102, 81, 51);
        put(Material.OAK_LEAVES, 48, 99, 48);
        put(Material.BEDROCK, 55, 55, 55);
        put(Material.NETHERRACK, 97, 38, 38);
        put(Material.END_STONE, 219, 223, 165);
        put(Material.DEEPSLATE, 80, 80, 82);
        put(Material.GRAVEL, 126, 126, 126);
        put(Material.CLAY, 159, 164, 177);
        put(Material.MYCELIUM, 111, 99, 99);
        put(Material.PODZOL, 129, 103, 65);
        put(Material.SANDSTONE, 218, 210, 158);
        put(Material.RED_SAND, 190, 102, 43);
        put(Material.TERRACOTTA, 152, 94, 67);
    }

    private void put(Material material, int r, int g, int b) {
        colors.put(material, new Color(r, g, b));
    }
}
