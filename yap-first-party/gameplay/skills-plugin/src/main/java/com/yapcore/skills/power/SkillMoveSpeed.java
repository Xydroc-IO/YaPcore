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
 *
 * <p>Creative/spectator fly uses abilities {@code flyingSpeed} (Bukkit {@link Player#setFlySpeed}),
 * not {@link Attribute#MOVEMENT_SPEED}. After applying walk modifiers, fly is synced to the same
 * ratio so sprint-while-flying stays a true 2× of the player's current (skill-boosted) speed
 * instead of a fixed vanilla "1".
 */
public final class SkillMoveSpeed {

    private static final String KEY = "skill_move_speed";
    /** Bukkit {@link Player#getFlySpeed()} vanilla default (abilities 0.05 × 2). */
    public static final float VANILLA_BUKKIT_FLY = 0.1f;

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
        if (multiplier > 1.0000001) {
            attr.addTransientModifier(new AttributeModifier(
                    key, multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        syncFlySpeed(player);
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
        syncFlySpeed(player);
    }

    /**
     * Scale Bukkit fly speed with effective walk attribute so client sprint-fly (×2 abilities)
     * doubles whatever the player's current move speed is.
     */
    public static void syncFlySpeed(Player player) {
        AttributeInstance attr = attribute(player);
        if (attr == null) {
            return;
        }
        float fly = bukkitFlyFromMoveSpeed(attr.getBaseValue(), attr.getValue());
        if (Math.abs(player.getFlySpeed() - fly) < 1.0e-4f) {
            return;
        }
        player.setFlySpeed(fly);
    }

    /**
     * @param moveBase  attribute base (vanilla / Tailor catalog ≈ 0.1)
     * @param moveValue effective attribute after modifiers
     * @return Bukkit fly in {@code [-1, 1]} (vanilla {@code 0.1} at ratio 1)
     */
    public static float bukkitFlyFromMoveSpeed(double moveBase, double moveValue) {
        double base = moveBase > 1.0e-9 ? moveBase : 0.1;
        double ratio = Math.max(0.0, moveValue / base);
        double fly = VANILLA_BUKKIT_FLY * ratio;
        return (float) Math.max(-1.0, Math.min(1.0, fly));
    }

    private static AttributeInstance attribute(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        return player.getAttribute(Attribute.MOVEMENT_SPEED);
    }
}
