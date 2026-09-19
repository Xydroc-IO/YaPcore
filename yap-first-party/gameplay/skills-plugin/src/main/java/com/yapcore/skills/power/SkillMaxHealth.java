package com.yapcore.skills.power;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Extra {@link Attribute#MAX_HEALTH} (Health skill). 2 HP = 1 heart. */
public final class SkillMaxHealth {

    private static final String KEY = "skill_max_health";

    private SkillMaxHealth() {
    }

    public static void apply(Plugin plugin, Player player, double extraHp) {
        AttributeInstance attr = attribute(player);
        if (attr == null || plugin == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        double oldMax = attr.getValue();
        if (attr.getModifier(key) != null) {
            attr.removeModifier(key);
        }
        if (extraHp > 0.0000001) {
            attr.addTransientModifier(new AttributeModifier(
                    key, extraHp, AttributeModifier.Operation.ADD_NUMBER));
        }
        double newMax = attr.getValue();
        if (newMax > oldMax && player.getHealth() > 0.0) {
            player.setHealth(Math.min(newMax, player.getHealth() + (newMax - oldMax)));
        } else if (player.getHealth() > newMax) {
            player.setHealth(Math.max(1.0, newMax));
        }
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
        if (player.getHealth() > attr.getValue()) {
            player.setHealth(Math.max(1.0, attr.getValue()));
        }
    }

    private static AttributeInstance attribute(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        try {
            return player.getAttribute(Attribute.MAX_HEALTH);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
