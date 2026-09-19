package com.yapcore.skills.power;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Extra {@link Attribute#BLOCK_INTERACTION_RANGE} on the player's region thread.
 * Additive blocks (not a multiplier) so it stacks on YaPEssentials base reach.
 */
public final class SkillPlaceReach {

    private static final String KEY = "skill_place_reach";

    private SkillPlaceReach() {
    }

    public static void apply(Plugin plugin, Player player, double extraBlocks) {
        AttributeInstance attr = attribute(player);
        if (attr == null || plugin == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
        if (extraBlocks <= 0.0000001) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                key, extraBlocks, AttributeModifier.Operation.ADD_NUMBER));
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
        try {
            return player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
