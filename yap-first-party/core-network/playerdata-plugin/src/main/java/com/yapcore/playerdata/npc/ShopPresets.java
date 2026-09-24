package com.yapcore.playerdata.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

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
            "weapons", "armor", "tools", "food", "blocks", "redstone", "crafting", "enchants",
            "farming", "fishing");

    public static Set<String> ids() {
        return Set.copyOf(PRESET_IDS);
    }

    public static List<OfferSpec> get(String id) {
        if (id == null) {
            return List.of();
        }
        return switch (id.trim().toLowerCase(Locale.ROOT)) {
            case "weapons" -> ShopPresetCombatCatalog.weapons();
            case "armor" -> ShopPresetCombatCatalog.armor();
            case "tools" -> ShopPresetCombatCatalog.tools();
            case "food" -> ShopPresetBuildCatalog.food();
            case "blocks" -> ShopPresetBuildCatalog.blocks();
            case "redstone" -> ShopPresetBuildCatalog.redstone();
            case "crafting" -> ShopPresetBuildCatalog.crafting();
            case "enchants" -> ShopPresetSpecialtyCatalog.enchants();
            case "farming", "tractor_supply", "tractor-supply", "tractor" -> ShopPresetSpecialtyCatalog.farming();
            case "fishing", "tackle_shack", "tackle-shack", "tackle" -> ShopPresetSpecialtyCatalog.fishing();
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
}
