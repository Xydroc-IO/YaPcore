package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

final class ShopPresetHelpers {

    private ShopPresetHelpers() {
    }

    static void book(List<ShopPresets.OfferSpec> out, Enchantment ench, int level, double buy) {
        addEnchanted(out, Material.ENCHANTED_BOOK, 1, buy, Map.of(ench, level));
    }

    static void addToolSet(List<ShopPresets.OfferSpec> o, String type,
                           double wood, double stone, double iron, double gold,
                           double diamond, double netherite) {
        add(o, mat("WOODEN_" + type), 1, wood, wood * 0.38);
        add(o, mat("STONE_" + type), 1, stone, stone * 0.38);
        add(o, mat("IRON_" + type), 1, iron, iron * 0.38);
        add(o, mat("GOLDEN_" + type), 1, gold, gold * 0.38);
        add(o, mat("DIAMOND_" + type), 1, diamond, diamond * 0.38);
        add(o, mat("NETHERITE_" + type), 1, netherite, netherite * 0.38);
    }

    static void addArmorSet(List<ShopPresets.OfferSpec> o, String tier,
                            double helm, double chest, double legs, double boots) {
        add(o, mat(tier + "_HELMET"), 1, helm, helm * 0.38);
        add(o, mat(tier + "_CHESTPLATE"), 1, chest, chest * 0.38);
        add(o, mat(tier + "_LEGGINGS"), 1, legs, legs * 0.38);
        add(o, mat(tier + "_BOOTS"), 1, boots, boots * 0.38);
    }

    static Material mat(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null) {
            throw new IllegalArgumentException("Unknown material: " + name);
        }
        return m;
    }

    static void add(List<ShopPresets.OfferSpec> out, Material mat, int amount, double buy, double sell) {
        out.add(new ShopPresets.OfferSpec(mat, amount, round(buy), round(sell)));
    }

    static void addEnchanted(List<ShopPresets.OfferSpec> out, Material mat, int amount, double buy,
                             Map<Enchantment, Integer> enchants) {
        out.add(new ShopPresets.OfferSpec(mat, amount, round(buy), 0, Map.copyOf(enchants)));
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
