package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ShopPresetBuildCatalog {
    private ShopPresetBuildCatalog() {
    }

    static List<ShopPresets.OfferSpec> food() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        ShopPresetHelpers.add(o, Material.BREAD, 16, 32, 12);
        ShopPresetHelpers.add(o, Material.COOKED_BEEF, 16, 64, 24);
        ShopPresetHelpers.add(o, Material.COOKED_PORKCHOP, 16, 60, 22);
        ShopPresetHelpers.add(o, Material.COOKED_CHICKEN, 16, 48, 18);
        ShopPresetHelpers.add(o, Material.COOKED_MUTTON, 16, 52, 20);
        ShopPresetHelpers.add(o, Material.COOKED_RABBIT, 8, 40, 14);
        ShopPresetHelpers.add(o, Material.COOKED_COD, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.COOKED_SALMON, 16, 44, 16);
        ShopPresetHelpers.add(o, Material.BAKED_POTATO, 16, 28, 10);
        ShopPresetHelpers.add(o, Material.GOLDEN_CARROT, 8, 120, 45);
        ShopPresetHelpers.add(o, Material.GOLDEN_APPLE, 1, 400, 140);
        ShopPresetHelpers.add(o, Material.ENCHANTED_GOLDEN_APPLE, 1, 3500, 1200);
        ShopPresetHelpers.add(o, Material.APPLE, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.MELON_SLICE, 16, 16, 5);
        ShopPresetHelpers.add(o, Material.SWEET_BERRIES, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.GLOW_BERRIES, 16, 20, 7);
        ShopPresetHelpers.add(o, Material.DRIED_KELP, 32, 20, 6);
        ShopPresetHelpers.add(o, Material.COOKIE, 16, 18, 6);
        ShopPresetHelpers.add(o, Material.PUMPKIN_PIE, 8, 48, 16);
        ShopPresetHelpers.add(o, Material.CAKE, 1, 80, 28);
        ShopPresetHelpers.add(o, Material.MUSHROOM_STEW, 1, 35, 12);
        ShopPresetHelpers.add(o, Material.RABBIT_STEW, 1, 55, 20);
        ShopPresetHelpers.add(o, Material.BEETROOT_SOUP, 1, 30, 10);
        ShopPresetHelpers.add(o, Material.HONEY_BOTTLE, 4, 60, 22);
        ShopPresetHelpers.add(o, Material.MILK_BUCKET, 1, 40, 14);
        return o;
    }
    static List<ShopPresets.OfferSpec> blocks() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        // Building staples (stacks)
        ShopPresetHelpers.add(o, Material.COBBLESTONE, 64, 16, 5);
        ShopPresetHelpers.add(o, Material.STONE, 64, 24, 8);
        ShopPresetHelpers.add(o, Material.DEEPSLATE, 64, 28, 9);
        ShopPresetHelpers.add(o, Material.GRANITE, 64, 20, 6);
        ShopPresetHelpers.add(o, Material.DIORITE, 64, 20, 6);
        ShopPresetHelpers.add(o, Material.ANDESITE, 64, 20, 6);
        ShopPresetHelpers.add(o, Material.DIRT, 64, 8, 2);
        ShopPresetHelpers.add(o, Material.GRASS_BLOCK, 64, 24, 8);
        ShopPresetHelpers.add(o, Material.SAND, 64, 16, 5);
        ShopPresetHelpers.add(o, Material.GRAVEL, 64, 14, 4);
        ShopPresetHelpers.add(o, Material.CLAY, 32, 40, 14);
        ShopPresetHelpers.add(o, Material.OAK_LOG, 64, 48, 16);
        ShopPresetHelpers.add(o, Material.SPRUCE_LOG, 64, 48, 16);
        ShopPresetHelpers.add(o, Material.BIRCH_LOG, 64, 48, 16);
        ShopPresetHelpers.add(o, Material.JUNGLE_LOG, 64, 52, 18);
        ShopPresetHelpers.add(o, Material.ACACIA_LOG, 64, 48, 16);
        ShopPresetHelpers.add(o, Material.DARK_OAK_LOG, 64, 52, 18);
        ShopPresetHelpers.add(o, Material.MANGROVE_LOG, 64, 56, 20);
        ShopPresetHelpers.add(o, Material.CHERRY_LOG, 64, 60, 22);
        ShopPresetHelpers.add(o, Material.OAK_PLANKS, 64, 32, 10);
        ShopPresetHelpers.add(o, Material.GLASS, 64, 80, 28);
        ShopPresetHelpers.add(o, Material.WHITE_WOOL, 64, 40, 14);
        ShopPresetHelpers.add(o, Material.BRICKS, 64, 90, 32);
        ShopPresetHelpers.add(o, Material.STONE_BRICKS, 64, 48, 16);
        ShopPresetHelpers.add(o, Material.DEEPSLATE_BRICKS, 64, 56, 18);
        ShopPresetHelpers.add(o, Material.QUARTZ_BLOCK, 32, 120, 40);
        ShopPresetHelpers.add(o, Material.PRISMARINE, 32, 100, 35);
        ShopPresetHelpers.add(o, Material.NETHERRACK, 64, 20, 6);
        ShopPresetHelpers.add(o, Material.BASALT, 64, 28, 9);
        ShopPresetHelpers.add(o, Material.BLACKSTONE, 64, 32, 10);
        ShopPresetHelpers.add(o, Material.END_STONE, 64, 40, 14);
        ShopPresetHelpers.add(o, Material.OBSIDIAN, 16, 320, 110);
        ShopPresetHelpers.add(o, Material.CRYING_OBSIDIAN, 8, 280, 95);
        ShopPresetHelpers.add(o, Material.GLOWSTONE, 32, 140, 50);
        ShopPresetHelpers.add(o, Material.SEA_LANTERN, 16, 160, 55);
        ShopPresetHelpers.add(o, Material.SHROOMLIGHT, 16, 100, 35);
        ShopPresetHelpers.add(o, Material.TORCH, 64, 20, 6);
        ShopPresetHelpers.add(o, Material.LANTERN, 16, 80, 28);
        ShopPresetHelpers.add(o, Material.SOUL_LANTERN, 8, 100, 35);
        // Ores / valuables
        ShopPresetHelpers.add(o, Material.COAL, 32, 64, 22);
        ShopPresetHelpers.add(o, Material.RAW_IRON, 16, 120, 40);
        ShopPresetHelpers.add(o, Material.RAW_GOLD, 16, 180, 60);
        ShopPresetHelpers.add(o, Material.RAW_COPPER, 32, 80, 28);
        ShopPresetHelpers.add(o, Material.IRON_INGOT, 16, 200, 70);
        ShopPresetHelpers.add(o, Material.GOLD_INGOT, 16, 280, 95);
        ShopPresetHelpers.add(o, Material.COPPER_INGOT, 16, 100, 35);
        ShopPresetHelpers.add(o, Material.DIAMOND, 1, 450, 160);
        ShopPresetHelpers.add(o, Material.EMERALD, 1, 200, 70);
        ShopPresetHelpers.add(o, Material.LAPIS_LAZULI, 32, 80, 28);
        ShopPresetHelpers.add(o, Material.REDSTONE, 64, 90, 30);
        ShopPresetHelpers.add(o, Material.QUARTZ, 32, 100, 35);
        ShopPresetHelpers.add(o, Material.NETHERITE_INGOT, 1, 3200, 1100);
        ShopPresetHelpers.add(o, Material.ANCIENT_DEBRIS, 1, 1800, 650);
        return o;
    }
    static List<ShopPresets.OfferSpec> redstone() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        ShopPresetHelpers.add(o, Material.REDSTONE, 64, 90, 30);
        ShopPresetHelpers.add(o, Material.REDSTONE_TORCH, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.REDSTONE_BLOCK, 8, 140, 50);
        ShopPresetHelpers.add(o, Material.REPEATER, 8, 80, 28);
        ShopPresetHelpers.add(o, Material.COMPARATOR, 8, 100, 35);
        ShopPresetHelpers.add(o, Material.OBSERVER, 8, 120, 42);
        ShopPresetHelpers.add(o, Material.PISTON, 8, 100, 35);
        ShopPresetHelpers.add(o, Material.STICKY_PISTON, 8, 140, 50);
        ShopPresetHelpers.add(o, Material.SLIME_BLOCK, 8, 160, 55);
        ShopPresetHelpers.add(o, Material.HONEY_BLOCK, 8, 140, 48);
        ShopPresetHelpers.add(o, Material.HOPPER, 4, 180, 65);
        ShopPresetHelpers.add(o, Material.DROPPER, 8, 90, 30);
        ShopPresetHelpers.add(o, Material.DISPENSER, 8, 110, 38);
        ShopPresetHelpers.add(o, Material.TARGET, 4, 80, 28);
        ShopPresetHelpers.add(o, Material.DAYLIGHT_DETECTOR, 4, 100, 35);
        ShopPresetHelpers.add(o, Material.TRIPWIRE_HOOK, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.LEVER, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.STONE_BUTTON, 16, 16, 5);
        ShopPresetHelpers.add(o, Material.OAK_BUTTON, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.STONE_PRESSURE_PLATE, 8, 20, 6);
        ShopPresetHelpers.add(o, Material.OAK_PRESSURE_PLATE, 8, 16, 5);
        ShopPresetHelpers.add(o, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 4, 80, 28);
        ShopPresetHelpers.add(o, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 4, 100, 35);
        ShopPresetHelpers.add(o, Material.NOTE_BLOCK, 4, 60, 20);
        ShopPresetHelpers.add(o, Material.JUKEBOX, 1, 200, 70);
        ShopPresetHelpers.add(o, Material.REDSTONE_LAMP, 8, 90, 30);
        ShopPresetHelpers.add(o, Material.SCULK_SENSOR, 2, 400, 140);
        ShopPresetHelpers.add(o, Material.CALIBRATED_SCULK_SENSOR, 1, 800, 280);
        ShopPresetHelpers.add(o, Material.TNT, 4, 200, 70);
        ShopPresetHelpers.add(o, Material.RAIL, 32, 80, 28);
        ShopPresetHelpers.add(o, Material.POWERED_RAIL, 16, 160, 55);
        ShopPresetHelpers.add(o, Material.DETECTOR_RAIL, 8, 100, 35);
        ShopPresetHelpers.add(o, Material.ACTIVATOR_RAIL, 8, 100, 35);
        ShopPresetHelpers.add(o, Material.MINECART, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.CHEST_MINECART, 1, 160, 55);
        ShopPresetHelpers.add(o, Material.HOPPER_MINECART, 1, 220, 75);
        ShopPresetHelpers.add(o, Material.TNT_MINECART, 1, 280, 95);
        return o;
    }
    static List<ShopPresets.OfferSpec> crafting() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        ShopPresetHelpers.add(o, Material.CRAFTING_TABLE, 1, 40, 14);
        ShopPresetHelpers.add(o, Material.FURNACE, 1, 60, 20);
        ShopPresetHelpers.add(o, Material.BLAST_FURNACE, 1, 180, 65);
        ShopPresetHelpers.add(o, Material.SMOKER, 1, 160, 55);
        ShopPresetHelpers.add(o, Material.CAMPFIRE, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.SOUL_CAMPFIRE, 1, 70, 24);
        ShopPresetHelpers.add(o, Material.ANVIL, 1, 400, 140);
        ShopPresetHelpers.add(o, Material.CHIPPED_ANVIL, 1, 250, 85);
        ShopPresetHelpers.add(o, Material.DAMAGED_ANVIL, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.SMITHING_TABLE, 1, 200, 70);
        ShopPresetHelpers.add(o, Material.GRINDSTONE, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.STONECUTTER, 1, 100, 35);
        ShopPresetHelpers.add(o, Material.CARTOGRAPHY_TABLE, 1, 90, 30);
        ShopPresetHelpers.add(o, Material.FLETCHING_TABLE, 1, 80, 28);
        ShopPresetHelpers.add(o, Material.LOOM, 1, 80, 28);
        ShopPresetHelpers.add(o, Material.COMPOSTER, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.BARREL, 1, 60, 20);
        ShopPresetHelpers.add(o, Material.CHEST, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.TRAPPED_CHEST, 1, 70, 24);
        ShopPresetHelpers.add(o, Material.ENDER_CHEST, 1, 600, 220);
        ShopPresetHelpers.add(o, Material.SHULKER_BOX, 1, 900, 320);
        ShopPresetHelpers.add(o, Material.BREWING_STAND, 1, 280, 95);
        ShopPresetHelpers.add(o, Material.CAULDRON, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.ENCHANTING_TABLE, 1, 1200, 420);
        ShopPresetHelpers.add(o, Material.BOOKSHELF, 8, 160, 55);
        ShopPresetHelpers.add(o, Material.LECTERN, 1, 100, 35);
        ShopPresetHelpers.add(o, Material.BEACON, 1, 8000, 2800);
        ShopPresetHelpers.add(o, Material.CONDUIT, 1, 2500, 900);
        ShopPresetHelpers.add(o, Material.RESPAWN_ANCHOR, 1, 800, 280);
        ShopPresetHelpers.add(o, Material.LODESTONE, 1, 1500, 520);
        ShopPresetHelpers.add(o, Material.BELL, 1, 400, 140);
        ShopPresetHelpers.add(o, Material.BUCKET, 1, 40, 14);
        ShopPresetHelpers.add(o, Material.WATER_BUCKET, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.LAVA_BUCKET, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.POWDER_SNOW_BUCKET, 1, 60, 20);
        return o;
    }
}
