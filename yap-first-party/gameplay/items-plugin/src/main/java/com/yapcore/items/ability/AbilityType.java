package com.yapcore.items.ability;

import java.util.Locale;

/** All shipped ability handlers. */
public enum AbilityType {
    MESSAGE,
    EFFECT,
    HEAL,
    FEED,
    LAUNCH,
    DASH,
    LIGHTNING,
    LIGHTNING_DASH,
    SMITE_TARGET,
    EXPLODE,
    PROJECTILE,
    COMMAND_PLAYER,
    COMMAND_CONSOLE,
    SOUND,
    PARTICLE,
    GIVE_ITEM,
    BREAK_BLOCK,
    AREA_EFFECT,
    /** Remove potion effects from the holder. */
    CLEANSE,
    /** Grant absorption hearts. */
    ABSORB,
    /** Launch a fireball. */
    FIREBALL,
    /** Pull nearby living entities toward the player. */
    PULL,
    /** Push / knockback nearby living entities. */
    PUSH,
    /** Instant look-direction blink (teleport). */
    BLINK,
    /** AoE damage around the player. */
    GROUND_SLAM,
    /** Repair durability on the held custom item. */
    REPAIR;

    public static AbilityType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ability type required");
        }
        return AbilityType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
