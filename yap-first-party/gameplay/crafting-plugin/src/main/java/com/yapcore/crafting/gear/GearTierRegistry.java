package com.yapcore.crafting.gear;

import com.yapcore.crafting.recipe.RecipeOutput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GearTierRegistry {

    public static final NamespacedKey GEAR_TIER_KEY = new NamespacedKey("yapcombat", "yap_gear_tier");

    private final Map<String, GearTier> tiers;

    public GearTierRegistry(Map<String, GearTier> tiers) {
        this.tiers = Map.copyOf(tiers);
    }

    public Map<String, GearTier> tiers() {
        return tiers;
    }

    public ItemStack createOutput(JavaPlugin plugin, RecipeOutput output) {
        ItemStack stack = new ItemStack(output.material(), output.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String tierId = output.gearTier();
        GearTier tier = tierId != null ? tiers.get(tierId.toLowerCase(Locale.ROOT)) : null;

        String display = output.displayName();
        if (display == null && tier != null && tier.displayPrefix() != null) {
            display = tier.displayPrefix() + " " + prettifyMaterial(output.material());
        }
        if (display != null) {
            meta.displayName(Component.text(display, NamedTextColor.WHITE));
        }

        if (tierId != null && !tierId.isBlank()) {
            meta.getPersistentDataContainer().set(
                    GEAR_TIER_KEY,
                    PersistentDataType.STRING,
                    tierId.toLowerCase(Locale.ROOT));
        }

        if (tier != null) {
            List<Component> lore = new ArrayList<>();
            if (tier.attackBonus() > 0) {
                lore.add(Component.text("Attack: +" + tier.attackBonus(), NamedTextColor.GRAY));
            }
            if (tier.strengthBonus() > 0) {
                lore.add(Component.text("Strength: +" + tier.strengthBonus(), NamedTextColor.GRAY));
            }
            if (tier.defenceBonus() > 0) {
                lore.add(Component.text("Defence: +" + tier.defenceBonus(), NamedTextColor.GRAY));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public static String readTier(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(GEAR_TIER_KEY, PersistentDataType.STRING);
    }

    private static String prettifyMaterial(org.bukkit.Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public record GearTier(
            String id,
            int attackBonus,
            int strengthBonus,
            int defenceBonus,
            String displayPrefix) {
    }
}
