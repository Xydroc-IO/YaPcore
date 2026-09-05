package com.yapcore.map;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared material / biome color tables for flat tiles and 3D meshes. */
public final class MapBlockColors {

    private final Map<Material, Color> colors = new EnumMap<>(Material.class);
    private final Map<String, Color> biomeColors = new ConcurrentHashMap<>();

    public MapBlockColors() {
        seedColors();
        seedBiomeColors();
    }

    public Color colorFor(Material material) {
        return colors.getOrDefault(material, colors.get(Material.STONE));
    }

    public int rgbFor(Material material) {
        return colorFor(material).getRGB() & 0xffffff;
    }

    public Color biomeColor(Biome biome) {
        if (biome == null) {
            return null;
        }
        String key;
        try {
            key = biome.getKey().getKey();
        } catch (Throwable t) {
            key = biome.toString().toLowerCase(Locale.ROOT);
        }
        return biomeColors.getOrDefault(key, biomeColors.get(key.toLowerCase(Locale.ROOT)));
    }

    public Color tinted(Color base, Biome biome, boolean biomeTint) {
        if (!biomeTint || base == null) {
            return base;
        }
        Color tint = biomeColor(biome);
        if (tint == null) {
            return base;
        }
        return blend(base, tint, 0.35f);
    }

    public int tintedRgb(Material material, Biome biome, boolean biomeTint) {
        Color c = tinted(colorFor(material), biome, biomeTint);
        return c.getRGB() & 0xffffff;
    }

    static Color blend(Color a, Color b, float bWeight) {
        float aw = 1f - bWeight;
        int r = Math.round(a.getRed() * aw + b.getRed() * bWeight);
        int g = Math.round(a.getGreen() * aw + b.getGreen() * bWeight);
        int bl = Math.round(a.getBlue() * aw + b.getBlue() * bWeight);
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void put(Material material, int r, int g, int b) {
        colors.put(material, new Color(r, g, b));
    }

    private void putBiome(String key, int r, int g, int b) {
        biomeColors.put(key, new Color(r, g, b));
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
        put(Material.COAL_ORE, 70, 70, 70);
        put(Material.IRON_ORE, 180, 160, 140);
        put(Material.COPPER_ORE, 160, 110, 80);
        put(Material.LAVA, 220, 90, 20);
        put(Material.OBSIDIAN, 20, 18, 30);
        put(Material.PACKED_ICE, 140, 180, 230);
        put(Material.BLUE_ICE, 100, 150, 220);
        put(Material.MOSS_BLOCK, 70, 120, 50);
        put(Material.MUD, 70, 60, 50);
        put(Material.SCULK, 20, 40, 45);
        put(Material.SOUL_SAND, 80, 60, 50);
        put(Material.SOUL_SOIL, 70, 55, 45);
        put(Material.BASALT, 60, 60, 65);
        put(Material.BLACKSTONE, 40, 35, 40);
        put(Material.CRIMSON_NYLIUM, 140, 40, 40);
        put(Material.WARPED_NYLIUM, 30, 120, 110);
    }

    private void seedBiomeColors() {
        putBiome("plains", 145, 189, 89);
        putBiome("sunflower_plains", 150, 195, 90);
        putBiome("forest", 55, 120, 45);
        putBiome("flower_forest", 70, 140, 60);
        putBiome("birch_forest", 100, 150, 80);
        putBiome("dark_forest", 35, 80, 35);
        putBiome("taiga", 60, 100, 70);
        putBiome("snowy_taiga", 180, 200, 210);
        putBiome("snowy_plains", 220, 230, 240);
        putBiome("ice_spikes", 200, 220, 255);
        putBiome("desert", 230, 210, 140);
        putBiome("badlands", 200, 110, 50);
        putBiome("wooded_badlands", 180, 100, 45);
        putBiome("savanna", 170, 160, 70);
        putBiome("jungle", 40, 130, 40);
        putBiome("sparse_jungle", 60, 140, 50);
        putBiome("swamp", 70, 90, 50);
        putBiome("mangrove_swamp", 50, 80, 55);
        putBiome("ocean", 50, 90, 180);
        putBiome("deep_ocean", 30, 60, 140);
        putBiome("warm_ocean", 40, 140, 180);
        putBiome("lukewarm_ocean", 45, 110, 170);
        putBiome("cold_ocean", 60, 100, 160);
        putBiome("frozen_ocean", 140, 180, 220);
        putBiome("river", 70, 120, 200);
        putBiome("beach", 220, 210, 160);
        putBiome("mushroom_fields", 150, 120, 150);
        putBiome("cherry_grove", 230, 180, 200);
        putBiome("meadow", 120, 180, 90);
        putBiome("grove", 140, 170, 150);
        putBiome("peaks", 160, 160, 170);
        putBiome("stony_peaks", 130, 130, 135);
        putBiome("jagged_peaks", 200, 210, 220);
        putBiome("frozen_peaks", 210, 220, 235);
        putBiome("nether_wastes", 120, 50, 40);
        putBiome("crimson_forest", 150, 40, 40);
        putBiome("warped_forest", 30, 120, 110);
        putBiome("soul_sand_valley", 70, 55, 45);
        putBiome("basalt_deltas", 60, 60, 65);
        putBiome("the_end", 200, 200, 160);
        putBiome("end_highlands", 210, 210, 170);
        putBiome("end_midlands", 195, 195, 155);
        putBiome("small_end_islands", 180, 180, 150);
        putBiome("end_barrens", 170, 170, 140);
    }
}
