package com.yapcore.claims;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;

/**
 * Classifies explosion sources for claim TNT / creeper-explosion flags.
 */
public final class ClaimExplosionRules {

    public enum Kind {
        NONE,
        TNT,
        CREEPER
    }

    private ClaimExplosionRules() {
    }

    public static Kind kindOf(Entity entity) {
        if (entity == null) {
            return Kind.NONE;
        }
        if (entity instanceof TNTPrimed) {
            return Kind.TNT;
        }
        if (entity instanceof Creeper) {
            return Kind.CREEPER;
        }
        EntityType type = entity.getType();
        if (type == EntityType.TNT || type == EntityType.TNT_MINECART) {
            return Kind.TNT;
        }
        if (type == EntityType.CREEPER) {
            return Kind.CREEPER;
        }
        return Kind.NONE;
    }

    /** Whether block/entity grief from this explosion is allowed at a claimed location. */
    public static boolean allowAtClaim(Kind kind, boolean tntFlagAllow, boolean creeperFlagAllow) {
        return switch (kind) {
            case TNT -> tntFlagAllow;
            case CREEPER -> creeperFlagAllow;
            case NONE -> true;
        };
    }
}
