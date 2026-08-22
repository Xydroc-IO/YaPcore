package com.yapcore.combat.projectile;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.projectile.CombatProjectileKeys;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class ProjectilePhysics {

    private ProjectilePhysics() {
    }

    public static void tagAndLaunch(
            Projectile projectile,
            Player shooter,
            float power,
            int pierce,
            CombatProjectileKeys keys,
            CombatConfig.ProjectileConfig config) {
        var pdc = projectile.getPersistentDataContainer();
        pdc.set(keys.managed(), PersistentDataType.BYTE, (byte) 1);
        pdc.set(keys.shooter(), PersistentDataType.STRING, shooter.getUniqueId().toString());
        pdc.set(keys.power(), PersistentDataType.FLOAT, power);
        pdc.set(keys.pierce(), PersistentDataType.INTEGER, pierce);
        pdc.set(keys.launchY(), PersistentDataType.DOUBLE, shooter.getLocation().getY());

        Vector velocity = projectile.getVelocity();
        double scale = config.velocityScale() * Math.max(0.5, power);
        velocity.multiply(scale);
        if (config.gravityMultiplier() != 1.0) {
            velocity.setY(velocity.getY() * config.gravityMultiplier());
        }
        projectile.setVelocity(velocity);
    }

    public static boolean isManaged(Projectile projectile, CombatProjectileKeys keys) {
        Byte flag = projectile.getPersistentDataContainer().get(keys.managed(), PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public static Player resolveShooter(Projectile projectile, CombatProjectileKeys keys) {
        String raw = projectile.getPersistentDataContainer().get(keys.shooter(), PersistentDataType.STRING);
        if (raw == null) {
            return projectile.getShooter() instanceof Player p ? p : null;
        }
        try {
            var uuid = java.util.UUID.fromString(raw);
            return org.bukkit.Bukkit.getPlayer(uuid);
        } catch (IllegalArgumentException e) {
            return projectile.getShooter() instanceof Player p ? p : null;
        }
    }

    public static float readPower(Projectile projectile, CombatProjectileKeys keys) {
        Float power = projectile.getPersistentDataContainer().get(keys.power(), PersistentDataType.FLOAT);
        return power == null ? 1.0f : power;
    }

    public static int readPierce(Projectile projectile, CombatProjectileKeys keys) {
        Integer pierce = projectile.getPersistentDataContainer().get(keys.pierce(), PersistentDataType.INTEGER);
        return pierce == null ? 0 : pierce;
    }

    public static void decrementPierce(Projectile projectile, CombatProjectileKeys keys) {
        int pierce = readPierce(projectile, keys);
        projectile.getPersistentDataContainer().set(keys.pierce(), PersistentDataType.INTEGER, Math.max(0, pierce - 1));
    }

    public static double travelBlocks(Projectile projectile, CombatProjectileKeys keys) {
        Double launchY = projectile.getPersistentDataContainer().get(keys.launchY(), PersistentDataType.DOUBLE);
        if (launchY == null) {
            return 0;
        }
        return Math.abs(projectile.getLocation().getY() - launchY);
    }

    public static double dropOffMultiplier(Projectile projectile, CombatProjectileKeys keys, CombatConfig.ProjectileConfig config) {
        if (!config.dropOffEnabled()) {
            return 1.0;
        }
        double blocks = travelBlocks(projectile, keys);
        double reduction = blocks * config.dropOffPerBlock();
        return Math.max(config.minDropOffMultiplier(), 1.0 - reduction);
    }

    public static boolean isHeadshot(Projectile projectile, LivingEntity victim) {
        if (!(projectile instanceof AbstractArrow)) {
            return false;
        }
        double eye = victim.getEyeLocation().getY();
        double hit = projectile.getLocation().getY();
        return hit >= eye - 0.35;
    }

    public static int pierceBonus(int rangedLevel, CombatConfig.ProjectileConfig config) {
        if (config.piercePerRangedLevels() <= 0) {
            return 0;
        }
        return rangedLevel / config.piercePerRangedLevels();
    }
}
