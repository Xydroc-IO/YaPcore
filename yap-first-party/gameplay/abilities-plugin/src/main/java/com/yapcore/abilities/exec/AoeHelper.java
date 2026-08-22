package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AoeHelper {

    private AoeHelper() {
    }

    public static List<LivingEntity> targetsAt(
            Player caster,
            Location center,
            AbilityDefinition ability,
            double radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (center == null || radius <= 0) {
            return out;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(caster)) {
                continue;
            }
            if (!TargetResolver.matchesFilter(living, ability)) {
                continue;
            }
            out.add(living);
        }
        return List.copyOf(out);
    }

    public static Location areaCenter(Player caster, AbilityDefinition ability, LivingEntity primary) {
        return switch (ability.targetMode()) {
            case SELF -> caster.getLocation();
            case AREA, GROUND -> {
                if (primary != null) {
                    yield primary.getLocation();
                }
                Location eye = caster.getEyeLocation();
                yield eye.add(eye.getDirection().normalize().multiply(Math.min(ability.range(), 8.0)));
            }
            default -> primary != null ? primary.getLocation() : caster.getLocation();
        };
    }
}
