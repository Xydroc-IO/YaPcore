package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Built-in NPC shop catalogs. Buy = shop sells to player; sell = shop buys from player
 * (~40% of buy). Stock is always unlimited ({@code -1}). Enchanted offers are buy-only.
 */
public final class ShopPresets {

    public record OfferSpec(
            Material material,
            int amount,
            double buyPrice,
            double sellPrice,
            Map<Enchantment, Integer> enchants
    ) {
        public OfferSpec(Material material, int amount, double buyPrice, double sellPrice) {
            this(material, amount, buyPrice, sellPrice, Map.of());
        }

        public boolean enchanted() {
            return enchants != null && !enchants.isEmpty();
        }
    }

    private ShopPresets() {
    }

    private static final List<String> PRESET_IDS = List.of(
            "weapons", "armor", "tools", "food", "blocks", "redstone", "crafting", "enchants");

    public static Set<String> ids() {
        return Set.copyOf(PRESET_IDS);
    }

    public static List<OfferSpec> get(String id) {
        if (id == null) {
            return List.of();
        }
        return switch (id.trim().toLowerCase(Locale.ROOT)) {
            case "weapons" -> weapons();
            case "armor" -> armor();
            case "tools" -> tools();
            case "food" -> food();
            case "blocks" -> blocks();
            case "redstone" -> redstone();
            case "crafting" -> crafting();
            case "enchants" -> enchants();
            default -> List.of();
        };
    }

    public static Map<String, List<OfferSpec>> all() {
        Map<String, List<OfferSpec>> out = new LinkedHashMap<>();
        for (String id : PRESET_IDS) {
            out.put(id, get(id));
        }
        return out;
    }

    /** Sharpness/Protection style gear — buy-only enchanted diamond/netherite. */
    private static List<OfferSpec> weapons() {
        List<OfferSpec> o = new ArrayList<>();
        // Plain tiers
        addToolSet(o, "SWORD", 35, 80, 220, 180, 900, 2800);
        add(o, Material.BOW, 1, 180, 70);
        add(o, Material.CROSSBOW, 1, 220, 85);
        add(o, Material.TRIDENT, 1, 1200, 450);
        add(o, Material.MACE, 1, 2200, 850);
        add(o, Material.SHIELD, 1, 160, 60);
        add(o, Material.ARROW, 16, 24, 8);
        add(o, Material.SPECTRAL_ARROW, 8, 40, 14);
        add(o, Material.TIPPED_ARROW, 8, 55, 18);
        // Enchanted diamond / netherite (buy-only)
        addEnchanted(o, Material.DIAMOND_SWORD, 1, 2400,
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        addEnchanted(o, Material.NETHERITE_SWORD, 1, 6500,
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2,
                        Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        addEnchanted(o, Material.BOW, 1, 1800,
                Map.of(Enchantment.POWER, 5, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3));
        addEnchanted(o, Material.CROSSBOW, 1, 2000,
                Map.of(Enchantment.QUICK_CHARGE, 3, Enchantment.MULTISHOT, 1, Enchantment.UNBREAKING, 3));
        addEnchanted(o, Material.TRIDENT, 1, 3200,
                Map.of(Enchantment.LOYALTY, 3, Enchantment.IMPALING, 5, Enchantment.UNBREAKING, 3));
        return o;
    }

    private static List<OfferSpec> armor() {
        List<OfferSpec> o = new ArrayList<>();
        addArmorSet(o, "LEATHER", 40, 65, 55, 35);
        addArmorSet(o, "CHAINMAIL", 120, 200, 170, 100);
        addArmorSet(o, "IRON", 200, 320, 280, 160);
        addArmorSet(o, "GOLDEN", 160, 260, 220, 130);
        addArmorSet(o, "DIAMOND", 700, 1100, 950, 550);
        addArmorSet(o, "NETHERITE", 2200, 3600, 3000, 1800);
        add(o, Material.TURTLE_HELMET, 1, 280, 100);
        add(o, Material.ELYTRA, 1, 4500, 1600);
        addEnchanted(o, Material.DIAMOND_CHESTPLATE, 1, 3200,
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        addEnchanted(o, Material.NETHERITE_CHESTPLATE, 1, 8500,
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3,
                        Enchantment.MENDING, 1, Enchantment.THORNS, 3));
        addEnchanted(o, Material.ELYTRA, 1, 9000,
                Map.of(Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        return o;
    }

    private static List<OfferSpec> tools() {
        List<OfferSpec> o = new ArrayList<>();
        addToolSet(o, "PICKAXE", 30, 70, 200, 160, 850, 2600);
        addToolSet(o, "AXE", 28, 65, 190, 150, 800, 2400);
        addToolSet(o, "SHOVEL", 20, 45, 120, 100, 500, 1500);
        addToolSet(o, "HOE", 18, 40, 100, 80, 400, 1200);
        add(o, Material.FLINT_AND_STEEL, 1, 40, 15);
        add(o, Material.SHEARS, 1, 50, 18);
        add(o, Material.FISHING_ROD, 1, 80, 30);
        add(o, Material.BRUSH, 1, 60, 22);
        addEnchanted(o, Material.DIAMOND_PICKAXE, 1, 2800,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        addEnchanted(o, Material.NETHERITE_PICKAXE, 1, 7200,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        addEnchanted(o, Material.DIAMOND_AXE, 1, 2400,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        return o;
    }

    private static List<OfferSpec> food() {
        List<OfferSpec> o = new ArrayList<>();
        add(o, Material.BREAD, 16, 32, 12);
        add(o, Material.COOKED_BEEF, 16, 64, 24);
        add(o, Material.COOKED_PORKCHOP, 16, 60, 22);
        add(o, Material.COOKED_CHICKEN, 16, 48, 18);
        add(o, Material.COOKED_MUTTON, 16, 52, 20);
        add(o, Material.COOKED_RABBIT, 8, 40, 14);
        add(o, Material.COOKED_COD, 16, 36, 12);
        add(o, Material.COOKED_SALMON, 16, 44, 16);
        add(o, Material.BAKED_POTATO, 16, 28, 10);
        add(o, Material.GOLDEN_CARROT, 8, 120, 45);
        add(o, Material.GOLDEN_APPLE, 1, 400, 140);
        add(o, Material.ENCHANTED_GOLDEN_APPLE, 1, 3500, 1200);
        add(o, Material.APPLE, 16, 24, 8);
        add(o, Material.MELON_SLICE, 16, 16, 5);
        add(o, Material.SWEET_BERRIES, 16, 12, 4);
        add(o, Material.GLOW_BERRIES, 16, 20, 7);
        add(o, Material.DRIED_KELP, 32, 20, 6);
        add(o, Material.COOKIE, 16, 18, 6);
        add(o, Material.PUMPKIN_PIE, 8, 48, 16);
        add(o, Material.CAKE, 1, 80, 28);
        add(o, Material.MUSHROOM_STEW, 1, 35, 12);
        add(o, Material.RABBIT_STEW, 1, 55, 20);
        add(o, Material.BEETROOT_SOUP, 1, 30, 10);
        add(o, Material.HONEY_BOTTLE, 4, 60, 22);
        add(o, Material.MILK_BUCKET, 1, 40, 14);
        return o;
    }

    private static List<OfferSpec> blocks() {
        List<OfferSpec> o = new ArrayList<>();
        // Building staples (stacks)
        add(o, Material.COBBLESTONE, 64, 16, 5);
        add(o, Material.STONE, 64, 24, 8);
        add(o, Material.DEEPSLATE, 64, 28, 9);
        add(o, Material.GRANITE, 64, 20, 6);
        add(o, Material.DIORITE, 64, 20, 6);
        add(o, Material.ANDESITE, 64, 20, 6);
        add(o, Material.DIRT, 64, 8, 2);
        add(o, Material.GRASS_BLOCK, 64, 24, 8);
        add(o, Material.SAND, 64, 16, 5);
        add(o, Material.GRAVEL, 64, 14, 4);
        add(o, Material.CLAY, 32, 40, 14);
        add(o, Material.OAK_LOG, 64, 48, 16);
        add(o, Material.SPRUCE_LOG, 64, 48, 16);
        add(o, Material.BIRCH_LOG, 64, 48, 16);
        add(o, Material.JUNGLE_LOG, 64, 52, 18);
        add(o, Material.ACACIA_LOG, 64, 48, 16);
        add(o, Material.DARK_OAK_LOG, 64, 52, 18);
        add(o, Material.MANGROVE_LOG, 64, 56, 20);
        add(o, Material.CHERRY_LOG, 64, 60, 22);
        add(o, Material.OAK_PLANKS, 64, 32, 10);
        add(o, Material.GLASS, 64, 80, 28);
        add(o, Material.WHITE_WOOL, 64, 40, 14);
        add(o, Material.BRICKS, 64, 90, 32);
        add(o, Material.STONE_BRICKS, 64, 48, 16);
        add(o, Material.DEEPSLATE_BRICKS, 64, 56, 18);
        add(o, Material.QUARTZ_BLOCK, 32, 120, 40);
        add(o, Material.PRISMARINE, 32, 100, 35);
        add(o, Material.NETHERRACK, 64, 20, 6);
        add(o, Material.BASALT, 64, 28, 9);
        add(o, Material.BLACKSTONE, 64, 32, 10);
        add(o, Material.END_STONE, 64, 40, 14);
        add(o, Material.OBSIDIAN, 16, 320, 110);
        add(o, Material.CRYING_OBSIDIAN, 8, 280, 95);
        add(o, Material.GLOWSTONE, 32, 140, 50);
        add(o, Material.SEA_LANTERN, 16, 160, 55);
        add(o, Material.SHROOMLIGHT, 16, 100, 35);
        add(o, Material.TORCH, 64, 20, 6);
        add(o, Material.LANTERN, 16, 80, 28);
        add(o, Material.SOUL_LANTERN, 8, 100, 35);
        // Ores / valuables
        add(o, Material.COAL, 32, 64, 22);
        add(o, Material.RAW_IRON, 16, 120, 40);
        add(o, Material.RAW_GOLD, 16, 180, 60);
        add(o, Material.RAW_COPPER, 32, 80, 28);
        add(o, Material.IRON_INGOT, 16, 200, 70);
        add(o, Material.GOLD_INGOT, 16, 280, 95);
        add(o, Material.COPPER_INGOT, 16, 100, 35);
        add(o, Material.DIAMOND, 1, 450, 160);
        add(o, Material.EMERALD, 1, 200, 70);
        add(o, Material.LAPIS_LAZULI, 32, 80, 28);
        add(o, Material.REDSTONE, 64, 90, 30);
        add(o, Material.QUARTZ, 32, 100, 35);
        add(o, Material.NETHERITE_INGOT, 1, 3200, 1100);
        add(o, Material.ANCIENT_DEBRIS, 1, 1800, 650);
        return o;
    }

    private static List<OfferSpec> redstone() {
        List<OfferSpec> o = new ArrayList<>();
        add(o, Material.REDSTONE, 64, 90, 30);
        add(o, Material.REDSTONE_TORCH, 16, 40, 14);
        add(o, Material.REDSTONE_BLOCK, 8, 140, 50);
        add(o, Material.REPEATER, 8, 80, 28);
        add(o, Material.COMPARATOR, 8, 100, 35);
        add(o, Material.OBSERVER, 8, 120, 42);
        add(o, Material.PISTON, 8, 100, 35);
        add(o, Material.STICKY_PISTON, 8, 140, 50);
        add(o, Material.SLIME_BLOCK, 8, 160, 55);
        add(o, Material.HONEY_BLOCK, 8, 140, 48);
        add(o, Material.HOPPER, 4, 180, 65);
        add(o, Material.DROPPER, 8, 90, 30);
        add(o, Material.DISPENSER, 8, 110, 38);
        add(o, Material.TARGET, 4, 80, 28);
        add(o, Material.DAYLIGHT_DETECTOR, 4, 100, 35);
        add(o, Material.TRIPWIRE_HOOK, 16, 48, 16);
        add(o, Material.LEVER, 16, 24, 8);
        add(o, Material.STONE_BUTTON, 16, 16, 5);
        add(o, Material.OAK_BUTTON, 16, 12, 4);
        add(o, Material.STONE_PRESSURE_PLATE, 8, 20, 6);
        add(o, Material.OAK_PRESSURE_PLATE, 8, 16, 5);
        add(o, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 4, 80, 28);
        add(o, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 4, 100, 35);
        add(o, Material.NOTE_BLOCK, 4, 60, 20);
        add(o, Material.JUKEBOX, 1, 200, 70);
        add(o, Material.REDSTONE_LAMP, 8, 90, 30);
        add(o, Material.SCULK_SENSOR, 2, 400, 140);
        add(o, Material.CALIBRATED_SCULK_SENSOR, 1, 800, 280);
        add(o, Material.TNT, 4, 200, 70);
        add(o, Material.RAIL, 32, 80, 28);
        add(o, Material.POWERED_RAIL, 16, 160, 55);
        add(o, Material.DETECTOR_RAIL, 8, 100, 35);
        add(o, Material.ACTIVATOR_RAIL, 8, 100, 35);
        add(o, Material.MINECART, 1, 120, 40);
        add(o, Material.CHEST_MINECART, 1, 160, 55);
        add(o, Material.HOPPER_MINECART, 1, 220, 75);
        add(o, Material.TNT_MINECART, 1, 280, 95);
        return o;
    }

    private static List<OfferSpec> crafting() {
        List<OfferSpec> o = new ArrayList<>();
        add(o, Material.CRAFTING_TABLE, 1, 40, 14);
        add(o, Material.FURNACE, 1, 60, 20);
        add(o, Material.BLAST_FURNACE, 1, 180, 65);
        add(o, Material.SMOKER, 1, 160, 55);
        add(o, Material.CAMPFIRE, 1, 50, 18);
        add(o, Material.SOUL_CAMPFIRE, 1, 70, 24);
        add(o, Material.ANVIL, 1, 400, 140);
        add(o, Material.CHIPPED_ANVIL, 1, 250, 85);
        add(o, Material.DAMAGED_ANVIL, 1, 120, 40);
        add(o, Material.SMITHING_TABLE, 1, 200, 70);
        add(o, Material.GRINDSTONE, 1, 120, 40);
        add(o, Material.STONECUTTER, 1, 100, 35);
        add(o, Material.CARTOGRAPHY_TABLE, 1, 90, 30);
        add(o, Material.FLETCHING_TABLE, 1, 80, 28);
        add(o, Material.LOOM, 1, 80, 28);
        add(o, Material.COMPOSTER, 1, 50, 18);
        add(o, Material.BARREL, 1, 60, 20);
        add(o, Material.CHEST, 1, 50, 18);
        add(o, Material.TRAPPED_CHEST, 1, 70, 24);
        add(o, Material.ENDER_CHEST, 1, 600, 220);
        add(o, Material.SHULKER_BOX, 1, 900, 320);
        add(o, Material.BREWING_STAND, 1, 280, 95);
        add(o, Material.CAULDRON, 1, 120, 40);
        add(o, Material.ENCHANTING_TABLE, 1, 1200, 420);
        add(o, Material.BOOKSHELF, 8, 160, 55);
        add(o, Material.LECTERN, 1, 100, 35);
        add(o, Material.BEACON, 1, 8000, 2800);
        add(o, Material.CONDUIT, 1, 2500, 900);
        add(o, Material.RESPAWN_ANCHOR, 1, 800, 280);
        add(o, Material.LODESTONE, 1, 1500, 520);
        add(o, Material.BELL, 1, 400, 140);
        add(o, Material.BUCKET, 1, 40, 14);
        add(o, Material.WATER_BUCKET, 1, 50, 18);
        add(o, Material.LAVA_BUCKET, 1, 120, 40);
        add(o, Material.POWDER_SNOW_BUCKET, 1, 60, 20);
        return o;
    }

    private static List<OfferSpec> enchants() {
        List<OfferSpec> o = new ArrayList<>();
        // Curated enchanted books — buy-only (sell would need exact NBT match)
        book(o, Enchantment.SHARPNESS, 5, 900);
        book(o, Enchantment.SMITE, 5, 700);
        book(o, Enchantment.BANE_OF_ARTHROPODS, 5, 500);
        book(o, Enchantment.KNOCKBACK, 2, 350);
        book(o, Enchantment.FIRE_ASPECT, 2, 550);
        book(o, Enchantment.LOOTING, 3, 800);
        book(o, Enchantment.SWEEPING_EDGE, 3, 450);
        book(o, Enchantment.POWER, 5, 850);
        book(o, Enchantment.PUNCH, 2, 400);
        book(o, Enchantment.FLAME, 1, 500);
        book(o, Enchantment.INFINITY, 1, 1200);
        book(o, Enchantment.PROTECTION, 4, 900);
        book(o, Enchantment.FIRE_PROTECTION, 4, 600);
        book(o, Enchantment.BLAST_PROTECTION, 4, 600);
        book(o, Enchantment.PROJECTILE_PROTECTION, 4, 600);
        book(o, Enchantment.FEATHER_FALLING, 4, 700);
        book(o, Enchantment.THORNS, 3, 650);
        book(o, Enchantment.DEPTH_STRIDER, 3, 550);
        book(o, Enchantment.FROST_WALKER, 2, 700);
        book(o, Enchantment.SOUL_SPEED, 3, 800);
        book(o, Enchantment.SWIFT_SNEAK, 3, 750);
        book(o, Enchantment.RESPIRATION, 3, 500);
        book(o, Enchantment.AQUA_AFFINITY, 1, 400);
        book(o, Enchantment.EFFICIENCY, 5, 850);
        book(o, Enchantment.SILK_TOUCH, 1, 1400);
        book(o, Enchantment.FORTUNE, 3, 1300);
        book(o, Enchantment.UNBREAKING, 3, 700);
        book(o, Enchantment.MENDING, 1, 2000);
        book(o, Enchantment.LUCK_OF_THE_SEA, 3, 450);
        book(o, Enchantment.LURE, 3, 450);
        book(o, Enchantment.LOYALTY, 3, 700);
        book(o, Enchantment.IMPALING, 5, 800);
        book(o, Enchantment.RIPTIDE, 3, 750);
        book(o, Enchantment.CHANNELING, 1, 900);
        book(o, Enchantment.MULTISHOT, 1, 800);
        book(o, Enchantment.PIERCING, 4, 700);
        book(o, Enchantment.QUICK_CHARGE, 3, 750);
        book(o, Enchantment.DENSITY, 5, 900);
        book(o, Enchantment.BREACH, 4, 850);
        book(o, Enchantment.WIND_BURST, 3, 950);
        add(o, Material.BOOK, 16, 48, 16);
        add(o, Material.EXPERIENCE_BOTTLE, 16, 320, 110);
        add(o, Material.LAPIS_LAZULI, 32, 80, 28);
        return o;
    }

    private static void book(List<OfferSpec> out, Enchantment ench, int level, double buy) {
        addEnchanted(out, Material.ENCHANTED_BOOK, 1, buy, Map.of(ench, level));
    }

    private static void addToolSet(List<OfferSpec> o, String type,
                                   double wood, double stone, double iron, double gold,
                                   double diamond, double netherite) {
        add(o, mat("WOODEN_" + type), 1, wood, wood * 0.38);
        add(o, mat("STONE_" + type), 1, stone, stone * 0.38);
        add(o, mat("IRON_" + type), 1, iron, iron * 0.38);
        add(o, mat("GOLDEN_" + type), 1, gold, gold * 0.38);
        add(o, mat("DIAMOND_" + type), 1, diamond, diamond * 0.38);
        add(o, mat("NETHERITE_" + type), 1, netherite, netherite * 0.38);
    }

    private static void addArmorSet(List<OfferSpec> o, String tier,
                                    double helm, double chest, double legs, double boots) {
        add(o, mat(tier + "_HELMET"), 1, helm, helm * 0.38);
        add(o, mat(tier + "_CHESTPLATE"), 1, chest, chest * 0.38);
        add(o, mat(tier + "_LEGGINGS"), 1, legs, legs * 0.38);
        add(o, mat(tier + "_BOOTS"), 1, boots, boots * 0.38);
    }

    private static Material mat(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null) {
            throw new IllegalArgumentException("Unknown material: " + name);
        }
        return m;
    }

    private static void add(List<OfferSpec> out, Material mat, int amount, double buy, double sell) {
        out.add(new OfferSpec(mat, amount, round(buy), round(sell)));
    }

    private static void addEnchanted(List<OfferSpec> out, Material mat, int amount, double buy,
                                     Map<Enchantment, Integer> enchants) {
        out.add(new OfferSpec(mat, amount, round(buy), 0, Map.copyOf(enchants)));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
