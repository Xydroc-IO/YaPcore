package com.yapcore.skills.power;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Transient {@link Attribute#BLOCK_BREAK_SPEED} multiplier on the breaking player's region thread.
 * Cleared when the block is not a skilled block, when the break ends, and on quit.
 */
public final class SkillBreakSpeed {

    private static final String KEY = "skill_break_speed";

    private SkillBreakSpeed() {
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
        return player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
    }
}
