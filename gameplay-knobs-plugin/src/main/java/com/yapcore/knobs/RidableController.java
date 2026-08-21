package com.yapcore.knobs;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * WASD ridable steering from the passenger's {@link org.bukkit.Input}.
 * Controllable mounts clear conflicting move AI and follow look + input.
 */
public final class RidableController {

    private RidableController() {
    }

    public static void tickMount(LivingEntity mount, Player rider, KnobsConfig.MobKnobs knobs) {
        if (knobs == null || !knobs.ridable() || !knobs.controllable()) {
            return;
        }
        if (!knobs.ridableInWater() && mount.isInWater()) {
            return;
        }
        if (mount.getLocation().getY() > knobs.ridableMaxY()) {
            Vector v = mount.getVelocity();
            mount.setVelocity(new Vector(v.getX() * 0.5, Math.min(v.getY(), 0), v.getZ() * 0.5));
            return;
        }

        var input = rider.getCurrentInput();
        float yaw = rider.getLocation().getYaw();
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);

        double forward = 0;
        double strafe = 0;
        if (input.isForward()) {
            forward += 1;
        }
        if (input.isBackward()) {
            forward -= 1;
        }
        if (input.isLeft()) {
            strafe += 1;
        }
        if (input.isRight()) {
            strafe -= 1;
        }

        double speed = baseSpeed(mount);
        if (input.isSprint()) {
            speed *= 1.45;
        }
        double mx = (strafe * cos + forward * sin) * speed;
        double mz = (forward * cos - strafe * sin) * speed;

        Vector vel = mount.getVelocity();
        double my = vel.getY();
        if (input.isJump() && (mount.isOnGround() || mount.isInWater())) {
            my = mount.isInWater() ? 0.35 : 0.45;
        } else if (!mount.isOnGround() && !mount.isInWater()) {
            my = Math.max(my - 0.08, -0.8);
        }

        if (forward != 0 || strafe != 0 || input.isJump()) {
            mount.setRotation(yaw, mount.getLocation().getPitch());
            mount.setVelocity(new Vector(mx, my, mz));
        } else if (mount.isOnGround()) {
            mount.setVelocity(new Vector(vel.getX() * 0.6, my, vel.getZ() * 0.6));
        }
    }

    public static boolean isControlling(LivingEntity vehicle, Player player, KnobsConfig config) {
        if (vehicle instanceof Player) {
            return false;
        }
        KnobsConfig.MobKnobs knobs = config.mob(vehicle.getType().name());
        return knobs != null && knobs.ridable() && knobs.controllable()
                && vehicle.getPassengers().contains(player);
    }

    private static double baseSpeed(LivingEntity mount) {
        AttributeInstance speed = mount.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getValue() > 0) {
            return Math.max(0.18, Math.min(0.55, speed.getValue() * 2.8));
        }
        return 0.28;
    }
}
