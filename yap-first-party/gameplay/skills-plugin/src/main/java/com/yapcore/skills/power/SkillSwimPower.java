package com.yapcore.skills.power;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Water movement + oxygen from the Swimming skill (region-thread safe).
 * Uses {@link Attribute#WATER_MOVEMENT_EFFICIENCY} and {@link Attribute#OXYGEN_BONUS}.
 */
public final class SkillSwimPower {

    private static final String WATER_KEY = "skill_water_move";
    private static final String OXYGEN_KEY = "skill_oxygen";

    private SkillSwimPower() {
    }

    public static void apply(Plugin plugin, Player player, double waterEfficiency, double oxygenBonus) {
        if (plugin == null || player == null || !player.isOnline()) {
            return;
        }
        setAdditive(plugin, player, Attribute.WATER_MOVEMENT_EFFICIENCY, WATER_KEY, waterEfficiency);
        setAdditive(plugin, player, Attribute.OXYGEN_BONUS, OXYGEN_KEY, oxygenBonus);
    }

    public static void clear(Plugin plugin, Player player) {
        if (plugin == null || player == null) {
            return;
        }
        clearKey(plugin, player, Attribute.WATER_MOVEMENT_EFFICIENCY, WATER_KEY);
        clearKey(plugin, player, Attribute.OXYGEN_BONUS, OXYGEN_KEY);
    }

    private static void setAdditive(
            Plugin plugin, Player player, Attribute attribute, String keyName, double amount) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
        if (amount <= 1.0e-9) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private static void clearKey(Plugin plugin, Player player, Attribute attribute, String keyName) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
    }
}
