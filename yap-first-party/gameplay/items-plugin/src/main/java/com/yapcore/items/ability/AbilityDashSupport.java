package com.yapcore.items.ability;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Dash destination / ray / damage helpers for {@link AbilityExecutor}. */
final class AbilityDashSupport {

    private AbilityDashSupport() {
    }

    /**
     * Walks along {@code dir} up to {@code range} and returns the farthest location where the
     * player can stand (feet + head clear). Stops before solids so ceilings/walls clip the dash.
     * Snaps down onto solid ground so mid-air landings don't feel floaty / glitchy.
     */
    static Location findDashDestination(Player player, Vector dir, double range) {
        Location start = player.getLocation();
        World world = start.getWorld();
        Location best = start.clone();
        if (world == null || range <= 0) {
            return best;
        }
        double step = 0.35;
        double max = Math.max(step, range);
        for (double d = step; d <= max + 1.0e-6; d += step) {
            Location cand = start.clone().add(dir.clone().multiply(Math.min(d, max)));
            if (!isPassableStanding(world, cand)) {
                break;
            }
            best = cand;
        }
        return snapDashToGround(world, best);
    }

    /** Drop onto the nearest solid under the feet (up to 10 blocks) while staying passable. */
    static Location snapDashToGround(World world, Location feet) {
        Location loc = feet.clone();
        // Prefer standing on a block: search downward from current feet.
        for (int drop = 0; drop <= 10; drop++) {
            Location tryFeet = loc.clone().subtract(0, drop, 0);
            Location below = tryFeet.clone().subtract(0, 0.05, 0);
            if (world.getBlockAt(below).getType().isSolid() && isPassableStanding(world, tryFeet)) {
                tryFeet.setX(Math.floor(tryFeet.getX()) + 0.5);
                tryFeet.setZ(Math.floor(tryFeet.getZ()) + 0.5);
                tryFeet.setY(Math.floor(below.getY()) + 1.0);
                return tryFeet;
            }
        }
        // No ground nearby — keep air spot but center in the block for less wonky camera.
        loc.setX(Math.floor(loc.getX()) + 0.5);
        loc.setZ(Math.floor(loc.getZ()) + 0.5);
        return loc;
    }

    static boolean isPassableStanding(World world, Location feet) {
        int x = feet.getBlockX();
        int y = feet.getBlockY();
        int z = feet.getBlockZ();
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        return !world.getBlockAt(x, y, z).getType().isSolid()
                && !world.getBlockAt(x, y + 1, z).getType().isSolid();
    }

    /** Damage &lt; 0 (create UI "kill") or huge values = instant kill. */
    static void applyAbilityDamage(LivingEntity living, Player source, double damage) {
        if (damage < 0 || damage >= 1_000_000D) {
            living.setHealth(0.0);
            return;
        }
        if (damage > 0) {
            living.damage(damage, source);
        }
    }

    static Location targetLocation(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getLocation().getDirection(), range);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(player.getWorld());
        }
        return player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(range));
    }

    static Entity rayTarget(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                range,
                e -> e instanceof LivingEntity && e != player);
        return hit == null ? null : hit.getHitEntity();
    }
}
