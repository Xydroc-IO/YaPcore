package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ShopPresetCombatCatalog {
    private ShopPresetCombatCatalog() {
    }

    static List<ShopPresets.OfferSpec> weapons() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        // Plain tiers
        ShopPresetHelpers.addToolSet(o, "SWORD", 35, 80, 220, 180, 900, 2800);
        ShopPresetHelpers.add(o, Material.BOW, 1, 180, 70);
        ShopPresetHelpers.add(o, Material.CROSSBOW, 1, 220, 85);
        ShopPresetHelpers.add(o, Material.TRIDENT, 1, 1200, 450);
        ShopPresetHelpers.add(o, Material.MACE, 1, 2200, 850);
        ShopPresetHelpers.add(o, Material.SHIELD, 1, 160, 60);
        ShopPresetHelpers.add(o, Material.ARROW, 16, 24, 8);
        ShopPresetHelpers.add(o, Material.SPECTRAL_ARROW, 8, 40, 14);
        ShopPresetHelpers.add(o, Material.TIPPED_ARROW, 8, 55, 18);
        // Enchanted diamond / netherite (buy-only)
        ShopPresetHelpers.addEnchanted(o, Material.DIAMOND_SWORD, 1, 2400,
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        ShopPresetHelpers.addEnchanted(o, Material.NETHERITE_SWORD, 1, 6500,
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2,
                        Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        ShopPresetHelpers.addEnchanted(o, Material.BOW, 1, 1800,
                Map.of(Enchantment.POWER, 5, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3));
        ShopPresetHelpers.addEnchanted(o, Material.CROSSBOW, 1, 2000,
                Map.of(Enchantment.QUICK_CHARGE, 3, Enchantment.MULTISHOT, 1, Enchantment.UNBREAKING, 3));
        ShopPresetHelpers.addEnchanted(o, Material.TRIDENT, 1, 3200,
                Map.of(Enchantment.LOYALTY, 3, Enchantment.IMPALING, 5, Enchantment.UNBREAKING, 3));
        return o;
    }
    static List<ShopPresets.OfferSpec> armor() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        ShopPresetHelpers.addArmorSet(o, "LEATHER", 40, 65, 55, 35);
        ShopPresetHelpers.addArmorSet(o, "CHAINMAIL", 120, 200, 170, 100);
        ShopPresetHelpers.addArmorSet(o, "IRON", 200, 320, 280, 160);
        ShopPresetHelpers.addArmorSet(o, "GOLDEN", 160, 260, 220, 130);
        ShopPresetHelpers.addArmorSet(o, "DIAMOND", 700, 1100, 950, 550);
        ShopPresetHelpers.addArmorSet(o, "NETHERITE", 2200, 3600, 3000, 1800);
        ShopPresetHelpers.add(o, Material.TURTLE_HELMET, 1, 280, 100);
        ShopPresetHelpers.add(o, Material.ELYTRA, 1, 4500, 1600);
        ShopPresetHelpers.addEnchanted(o, Material.DIAMOND_CHESTPLATE, 1, 3200,
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        ShopPresetHelpers.addEnchanted(o, Material.NETHERITE_CHESTPLATE, 1, 8500,
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3,
                        Enchantment.MENDING, 1, Enchantment.THORNS, 3));
        ShopPresetHelpers.addEnchanted(o, Material.ELYTRA, 1, 9000,
                Map.of(Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        return o;
    }
    static List<ShopPresets.OfferSpec> tools() {
        List<ShopPresets.OfferSpec> o = new ArrayList<>();
        ShopPresetHelpers.addToolSet(o, "PICKAXE", 30, 70, 200, 160, 850, 2600);
        ShopPresetHelpers.addToolSet(o, "AXE", 28, 65, 190, 150, 800, 2400);
        ShopPresetHelpers.addToolSet(o, "SHOVEL", 20, 45, 120, 100, 500, 1500);
        ShopPresetHelpers.addToolSet(o, "HOE", 18, 40, 100, 80, 400, 1200);
        ShopPresetHelpers.add(o, Material.FLINT_AND_STEEL, 1, 40, 15);
        ShopPresetHelpers.add(o, Material.SHEARS, 1, 50, 18);
        ShopPresetHelpers.add(o, Material.FISHING_ROD, 1, 80, 30);
        ShopPresetHelpers.add(o, Material.BRUSH, 1, 60, 22);
        ShopPresetHelpers.addEnchanted(o, Material.DIAMOND_PICKAXE, 1, 2800,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        ShopPresetHelpers.addEnchanted(o, Material.NETHERITE_PICKAXE, 1, 7200,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        ShopPresetHelpers.addEnchanted(o, Material.DIAMOND_AXE, 1, 2400,
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        return o;
    }
}
