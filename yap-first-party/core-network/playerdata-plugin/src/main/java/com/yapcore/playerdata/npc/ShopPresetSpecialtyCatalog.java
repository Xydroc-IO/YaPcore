package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ShopPresetSpecialtyCatalog {
    private ShopPresetSpecialtyCatalog() {
    }

    static List<ShopPresets.OfferSpec> enchants() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        // Curated enchanted books — buy-only (sell would need exact NBT match)
        ShopPresetHelpers.book(o, Enchantment.SHARPNESS, 5, 900);
        ShopPresetHelpers.book(o, Enchantment.SMITE, 5, 700);
        ShopPresetHelpers.book(o, Enchantment.BANE_OF_ARTHROPODS, 5, 500);
        ShopPresetHelpers.book(o, Enchantment.KNOCKBACK, 2, 350);
        ShopPresetHelpers.book(o, Enchantment.FIRE_ASPECT, 2, 550);
        ShopPresetHelpers.book(o, Enchantment.LOOTING, 3, 800);
        ShopPresetHelpers.book(o, Enchantment.SWEEPING_EDGE, 3, 450);
        ShopPresetHelpers.book(o, Enchantment.POWER, 5, 850);
        ShopPresetHelpers.book(o, Enchantment.PUNCH, 2, 400);
        ShopPresetHelpers.book(o, Enchantment.FLAME, 1, 500);
        ShopPresetHelpers.book(o, Enchantment.INFINITY, 1, 1200);
        ShopPresetHelpers.book(o, Enchantment.PROTECTION, 4, 900);
        ShopPresetHelpers.book(o, Enchantment.FIRE_PROTECTION, 4, 600);
        ShopPresetHelpers.book(o, Enchantment.BLAST_PROTECTION, 4, 600);
        ShopPresetHelpers.book(o, Enchantment.PROJECTILE_PROTECTION, 4, 600);
        ShopPresetHelpers.book(o, Enchantment.FEATHER_FALLING, 4, 700);
        ShopPresetHelpers.book(o, Enchantment.THORNS, 3, 650);
        ShopPresetHelpers.book(o, Enchantment.DEPTH_STRIDER, 3, 550);
        ShopPresetHelpers.book(o, Enchantment.FROST_WALKER, 2, 700);
        ShopPresetHelpers.book(o, Enchantment.SOUL_SPEED, 3, 800);
        ShopPresetHelpers.book(o, Enchantment.SWIFT_SNEAK, 3, 750);
        ShopPresetHelpers.book(o, Enchantment.RESPIRATION, 3, 500);
        ShopPresetHelpers.book(o, Enchantment.AQUA_AFFINITY, 1, 400);
        ShopPresetHelpers.book(o, Enchantment.EFFICIENCY, 5, 850);
        ShopPresetHelpers.book(o, Enchantment.SILK_TOUCH, 1, 1400);
        ShopPresetHelpers.book(o, Enchantment.FORTUNE, 3, 1300);
        ShopPresetHelpers.book(o, Enchantment.UNBREAKING, 3, 700);
        ShopPresetHelpers.book(o, Enchantment.MENDING, 1, 2000);
        ShopPresetHelpers.book(o, Enchantment.LUCK_OF_THE_SEA, 3, 450);
        ShopPresetHelpers.book(o, Enchantment.LURE, 3, 450);
        ShopPresetHelpers.book(o, Enchantment.LOYALTY, 3, 700);
        ShopPresetHelpers.book(o, Enchantment.IMPALING, 5, 800);
        ShopPresetHelpers.book(o, Enchantment.RIPTIDE, 3, 750);
        ShopPresetHelpers.book(o, Enchantment.CHANNELING, 1, 900);
        ShopPresetHelpers.book(o, Enchantment.MULTISHOT, 1, 800);
        ShopPresetHelpers.book(o, Enchantment.PIERCING, 4, 700);
        ShopPresetHelpers.book(o, Enchantment.QUICK_CHARGE, 3, 750);
        ShopPresetHelpers.book(o, Enchantment.DENSITY, 5, 900);
        ShopPresetHelpers.book(o, Enchantment.BREACH, 4, 850);
        ShopPresetHelpers.book(o, Enchantment.WIND_BURST, 3, 950);
        ShopPresetHelpers.add(o, Material.BOOK, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.EXPERIENCE_BOTTLE, 16, 320, 110);
        ShopPresetHelpers.add(o, Material.LAPIS_LAZULI, 32, 80, 28);
        return o;
    }
    static List<ShopPresets.OfferSpec> farming() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        // --- Seeds & pods ---
        ShopPresetHelpers.add(o, Material.WHEAT_SEEDS, 32, 24, 8);
        ShopPresetHelpers.add(o, Material.BEETROOT_SEEDS, 32, 28, 9);
        ShopPresetHelpers.add(o, Material.MELON_SEEDS, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.PUMPKIN_SEEDS, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.TORCHFLOWER_SEEDS, 8, 120, 40);
        ShopPresetHelpers.add(o, Material.PITCHER_POD, 8, 120, 40);
        // --- Plantable crops / produce ---
        ShopPresetHelpers.add(o, Material.WHEAT, 32, 40, 14);
        ShopPresetHelpers.add(o, Material.BEETROOT, 32, 36, 12);
        ShopPresetHelpers.add(o, Material.CARROT, 32, 40, 14);
        ShopPresetHelpers.add(o, Material.POTATO, 32, 36, 12);
        ShopPresetHelpers.add(o, Material.POISONOUS_POTATO, 8, 8, 2);
        ShopPresetHelpers.add(o, Material.NETHER_WART, 32, 80, 28);
        ShopPresetHelpers.add(o, Material.SUGAR_CANE, 32, 48, 16);
        ShopPresetHelpers.add(o, Material.BAMBOO, 32, 32, 10);
        ShopPresetHelpers.add(o, Material.CACTUS, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.KELP, 32, 28, 9);
        ShopPresetHelpers.add(o, Material.SEA_PICKLE, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.SWEET_BERRIES, 32, 24, 8);
        ShopPresetHelpers.add(o, Material.GLOW_BERRIES, 32, 36, 12);
        ShopPresetHelpers.add(o, Material.COCOA_BEANS, 32, 44, 15);
        ShopPresetHelpers.add(o, Material.APPLE, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.MELON_SLICE, 32, 20, 6);
        ShopPresetHelpers.add(o, Material.MELON, 8, 48, 16);
        ShopPresetHelpers.add(o, Material.PUMPKIN, 8, 40, 14);
        ShopPresetHelpers.add(o, Material.CARVED_PUMPKIN, 4, 36, 12);
        ShopPresetHelpers.add(o, Material.HAY_BLOCK, 8, 90, 30);
        // --- Saplings & tree starters ---
        ShopPresetHelpers.add(o, Material.OAK_SAPLING, 16, 32, 10);
        ShopPresetHelpers.add(o, Material.SPRUCE_SAPLING, 16, 32, 10);
        ShopPresetHelpers.add(o, Material.BIRCH_SAPLING, 16, 32, 10);
        ShopPresetHelpers.add(o, Material.JUNGLE_SAPLING, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.ACACIA_SAPLING, 16, 32, 10);
        ShopPresetHelpers.add(o, Material.DARK_OAK_SAPLING, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.CHERRY_SAPLING, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.MANGROVE_PROPAGULE, 16, 44, 15);
        ShopPresetHelpers.add(o, Material.PALE_OAK_SAPLING, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.AZALEA, 8, 40, 14);
        ShopPresetHelpers.add(o, Material.FLOWERING_AZALEA, 8, 56, 18);
        // --- Farm soil & ground ---
        ShopPresetHelpers.add(o, Material.DIRT, 64, 8, 2);
        ShopPresetHelpers.add(o, Material.COARSE_DIRT, 64, 12, 3);
        ShopPresetHelpers.add(o, Material.ROOTED_DIRT, 32, 36, 12);
        ShopPresetHelpers.add(o, Material.GRASS_BLOCK, 64, 24, 8);
        ShopPresetHelpers.add(o, Material.PODZOL, 32, 40, 14);
        ShopPresetHelpers.add(o, Material.MYCELIUM, 32, 60, 20);
        ShopPresetHelpers.add(o, Material.MOSS_BLOCK, 32, 48, 16);
        ShopPresetHelpers.add(o, Material.PALE_MOSS_BLOCK, 32, 52, 18);
        ShopPresetHelpers.add(o, Material.MUD, 64, 16, 5);
        ShopPresetHelpers.add(o, Material.MUDDY_MANGROVE_ROOTS, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.FARMLAND, 32, 28, 9);
        // --- Bone meal & compost ---
        ShopPresetHelpers.add(o, Material.BONE_MEAL, 64, 64, 22);
        ShopPresetHelpers.add(o, Material.BONE_BLOCK, 16, 90, 30);
        ShopPresetHelpers.add(o, Material.BONE, 32, 36, 12);
        ShopPresetHelpers.add(o, Material.COMPOSTER, 1, 50, 18);
        // --- Fungi & nether growables ---
        ShopPresetHelpers.add(o, Material.BROWN_MUSHROOM, 16, 28, 9);
        ShopPresetHelpers.add(o, Material.RED_MUSHROOM, 16, 28, 9);
        ShopPresetHelpers.add(o, Material.CRIMSON_FUNGUS, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.WARPED_FUNGUS, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.CRIMSON_ROOTS, 16, 20, 6);
        ShopPresetHelpers.add(o, Material.WARPED_ROOTS, 16, 20, 6);
        ShopPresetHelpers.add(o, Material.NETHER_SPROUTS, 32, 16, 5);
        ShopPresetHelpers.add(o, Material.HANGING_ROOTS, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.SPORE_BLOSSOM, 4, 80, 28);
        ShopPresetHelpers.add(o, Material.GLOW_LICHEN, 16, 32, 10);
        ShopPresetHelpers.add(o, Material.VINE, 32, 20, 6);
        // --- Flowers & decorative plants ---
        ShopPresetHelpers.add(o, Material.DANDELION, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.POPPY, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.BLUE_ORCHID, 16, 16, 5);
        ShopPresetHelpers.add(o, Material.ALLIUM, 16, 16, 5);
        ShopPresetHelpers.add(o, Material.AZURE_BLUET, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.RED_TULIP, 16, 14, 4);
        ShopPresetHelpers.add(o, Material.ORANGE_TULIP, 16, 14, 4);
        ShopPresetHelpers.add(o, Material.WHITE_TULIP, 16, 14, 4);
        ShopPresetHelpers.add(o, Material.PINK_TULIP, 16, 14, 4);
        ShopPresetHelpers.add(o, Material.OXEYE_DAISY, 16, 12, 4);
        ShopPresetHelpers.add(o, Material.CORNFLOWER, 16, 14, 4);
        ShopPresetHelpers.add(o, Material.LILY_OF_THE_VALLEY, 16, 16, 5);
        ShopPresetHelpers.add(o, Material.TORCHFLOWER, 8, 80, 28);
        ShopPresetHelpers.add(o, Material.PITCHER_PLANT, 4, 100, 35);
        ShopPresetHelpers.add(o, Material.SUNFLOWER, 8, 28, 9);
        ShopPresetHelpers.add(o, Material.LILAC, 8, 24, 8);
        ShopPresetHelpers.add(o, Material.ROSE_BUSH, 8, 24, 8);
        ShopPresetHelpers.add(o, Material.PEONY, 8, 24, 8);
        ShopPresetHelpers.add(o, Material.PINK_PETALS, 16, 20, 6);
        ShopPresetHelpers.add(o, Material.SHORT_GRASS, 32, 8, 2);
        ShopPresetHelpers.add(o, Material.TALL_GRASS, 16, 12, 3);
        ShopPresetHelpers.add(o, Material.FERN, 16, 10, 3);
        ShopPresetHelpers.add(o, Material.LARGE_FERN, 8, 16, 5);
        ShopPresetHelpers.add(o, Material.SEAGRASS, 32, 12, 4);
        // --- Water / bees / eggs for farm setups ---
        ShopPresetHelpers.add(o, Material.WATER_BUCKET, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.HONEYCOMB, 8, 60, 20);
        ShopPresetHelpers.add(o, Material.HONEY_BOTTLE, 4, 60, 22);
        ShopPresetHelpers.add(o, Material.BEEHIVE, 1, 180, 65);
        ShopPresetHelpers.add(o, Material.BEE_NEST, 1, 220, 75);
        ShopPresetHelpers.add(o, Material.EGG, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.TURTLE_EGG, 4, 120, 40);
        ShopPresetHelpers.add(o, Material.SNIFFER_EGG, 1, 900, 300);
        return o;
    }
    static List<ShopPresets.OfferSpec> fishing() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        // --- Rods ---
        ShopPresetHelpers.add(o, Material.FISHING_ROD, 1, 80, 30);
        ShopPresetHelpers.add(o, Material.CARROT_ON_A_STICK, 1, 60, 22);
        ShopPresetHelpers.add(o, Material.WARPED_FUNGUS_ON_A_STICK, 1, 70, 26);
        ShopPresetHelpers.addEnchanted(o, Material.FISHING_ROD, 1, 450,
                Map.of(Enchantment.LURE, 3, Enchantment.LUCK_OF_THE_SEA, 3, Enchantment.UNBREAKING, 2));
        ShopPresetHelpers.addEnchanted(o, Material.FISHING_ROD, 1, 1200,
                Map.of(Enchantment.LURE, 3, Enchantment.LUCK_OF_THE_SEA, 3,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        // --- Bait & catch (raw) ---
        ShopPresetHelpers.add(o, Material.COD, 16, 28, 10);
        ShopPresetHelpers.add(o, Material.SALMON, 16, 32, 11);
        ShopPresetHelpers.add(o, Material.TROPICAL_FISH, 8, 40, 14);
        ShopPresetHelpers.add(o, Material.PUFFERFISH, 8, 48, 16);
        ShopPresetHelpers.add(o, Material.COOKED_COD, 16, 36, 12);
        ShopPresetHelpers.add(o, Material.COOKED_SALMON, 16, 44, 16);
        ShopPresetHelpers.add(o, Material.DRIED_KELP, 32, 20, 6);
        ShopPresetHelpers.add(o, Material.BREAD, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.ROTTEN_FLESH, 16, 12, 4);
        // --- Buckets & water ---
        ShopPresetHelpers.add(o, Material.WATER_BUCKET, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.COD_BUCKET, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.SALMON_BUCKET, 1, 140, 48);
        ShopPresetHelpers.add(o, Material.TROPICAL_FISH_BUCKET, 1, 160, 55);
        ShopPresetHelpers.add(o, Material.PUFFERFISH_BUCKET, 1, 180, 60);
        ShopPresetHelpers.add(o, Material.AXOLOTL_BUCKET, 1, 280, 95);
        ShopPresetHelpers.add(o, Material.TADPOLE_BUCKET, 1, 100, 35);
        // --- Coastal / craft supplies ---
        ShopPresetHelpers.add(o, Material.STRING, 16, 32, 11);
        ShopPresetHelpers.add(o, Material.STICK, 32, 8, 2);
        ShopPresetHelpers.add(o, Material.LILY_PAD, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.KELP, 32, 28, 9);
        ShopPresetHelpers.add(o, Material.SEA_PICKLE, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.SEAGRASS, 32, 12, 4);
        ShopPresetHelpers.add(o, Material.INK_SAC, 16, 28, 9);
        ShopPresetHelpers.add(o, Material.GLOW_INK_SAC, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.TURTLE_SCUTE, 4, 80, 28);
        ShopPresetHelpers.add(o, Material.TURTLE_EGG, 4, 120, 40);
        ShopPresetHelpers.add(o, Material.NAUTILUS_SHELL, 4, 200, 70);
        ShopPresetHelpers.add(o, Material.HEART_OF_THE_SEA, 1, 900, 320);
        ShopPresetHelpers.add(o, Material.PRISMARINE_SHARD, 16, 40, 14);
        ShopPresetHelpers.add(o, Material.PRISMARINE_CRYSTALS, 16, 48, 16);
        ShopPresetHelpers.add(o, Material.SPONGE, 4, 180, 60);
        ShopPresetHelpers.add(o, Material.WET_SPONGE, 4, 160, 55);
        // --- Boats & gear ---
        ShopPresetHelpers.add(o, Material.OAK_BOAT, 1, 40, 14);
        ShopPresetHelpers.add(o, Material.OAK_CHEST_BOAT, 1, 80, 28);
        ShopPresetHelpers.add(o, Material.SPYGLASS, 1, 120, 40);
        ShopPresetHelpers.add(o, Material.COMPASS, 1, 60, 22);
        ShopPresetHelpers.add(o, Material.MAP, 1, 40, 14);
        ShopPresetHelpers.add(o, Material.LEAD, 4, 48, 16);
        ShopPresetHelpers.add(o, Material.NAME_TAG, 1, 220, 75);
        ShopPresetHelpers.add(o, Material.TRIDENT, 1, 1200, 450);
        // --- Fishing enchants (books) ---
        ShopPresetHelpers.book(o, Enchantment.LURE, 3, 450);
        ShopPresetHelpers.book(o, Enchantment.LUCK_OF_THE_SEA, 3, 450);
        ShopPresetHelpers.book(o, Enchantment.UNBREAKING, 3, 700);
        ShopPresetHelpers.book(o, Enchantment.MENDING, 1, 2000);
        return o;
    }
}
