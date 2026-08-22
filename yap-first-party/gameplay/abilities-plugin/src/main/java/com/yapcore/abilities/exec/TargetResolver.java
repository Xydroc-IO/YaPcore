package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Set;

public final class TargetResolver {

    private static final Set<EntityType> UNDEAD_TYPES = Set.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON,
            EntityType.PHANTOM, EntityType.WITHER, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.ZOGLIN, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.ZOMBIE_VILLAGER, EntityType.GIANT);

    private TargetResolver() {
    }

    public static LivingEntity resolve(Player player, AbilityDefinition ability) {
        return switch (ability.targetMode()) {
            case SELF -> player;
            case NONE, AREA, GROUND -> null;
            case RAYCAST -> raycast(player, ability.range());
        };
    }

    public static boolean matchesFilter(LivingEntity target, AbilityDefinition ability) {
        if (!ability.hasTargetFilter()) {
            return true;
        }
        String filter = ability.targetFilter().trim();
        if ("undead".equalsIgnoreCase(filter)) {
            return UNDEAD_TYPES.contains(target.getType());
        }
        if ("player".equalsIgnoreCase(filter)) {
            return target instanceof Player;
        }
        if ("mob".equalsIgnoreCase(filter)) {
            return !(target instanceof Player);
        }
        return true;
    }

    private static LivingEntity raycast(Player player, double range) {
        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity living && !living.equals(player));
        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity living)) {
            return null;
        }
        return living;
    }
}
