package com.yapcore.skills.power;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Persistent-while-online {@link Attribute#MOVEMENT_SPEED} multiplier on the player's region thread.
 * Marathon only. Cleared on quit, spectator, and disable.
 */
public final class SkillMoveSpeed {

    private static final String KEY = "skill_move_speed";

    private SkillMoveSpeed() {
    }

    public static void apply(Plugin plugin, Player player, double multiplier) {
        AttributeInstance attr = attribute(player);
        if (attr == null || plugin == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
        if (multiplier <= 1.0000001) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                key, multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    public static void clear(Plugin plugin, Player player) {
        AttributeInstance attr = attribute(player);
        if (attr == null || plugin == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
    }

    private static AttributeInstance attribute(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        return player.getAttribute(Attribute.MOVEMENT_SPEED);
    }
}
