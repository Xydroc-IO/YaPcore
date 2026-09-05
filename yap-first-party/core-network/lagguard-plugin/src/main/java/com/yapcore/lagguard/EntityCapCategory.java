package com.yapcore.lagguard;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/** Soft spawn-budget categories for {@code entity-caps} config. */
public enum EntityCapCategory {
    ITEMS,
    MOBS,
    PROJECTILES,
    OTHER;

    public static EntityCapCategory of(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return OTHER;
        }
        if (entity instanceof Item) {
            return ITEMS;
        }
        if (entity instanceof Projectile) {
            return PROJECTILES;
        }
        if (entity instanceof LivingEntity) {
            return MOBS;
        }
        return OTHER;
    }

    /** Pure classification for unit tests (no Bukkit entity instances). */
    public static EntityCapCategory classify(boolean item, boolean projectile, boolean livingNonPlayer) {
        if (item) {
            return ITEMS;
        }
        if (projectile) {
            return PROJECTILES;
        }
        if (livingNonPlayer) {
            return MOBS;
        }
        return OTHER;
    }
}
