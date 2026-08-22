package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.api.VehicleController;
import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.api.event.VehicleCollideEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Non-vanilla vehicle physics: traction, lateral slip, yaw inertia, slope force,
 * rolling resistance, surface grip, handbrake.
 */
final class VehiclePhysics {

    private VehiclePhysics() {
    }

    static void tick(VehicleInstance vehicle, VehiclesConfig config) {
        VehicleType type = vehicle.getType();
        Player driver = vehicle.getDriver();
        VehicleController.ControlInput input = new VehicleController.ControlInput();
        type.controller().apply(vehicle, driver, input);

        double speed = vehicle.getSpeed();
        double lateral = vehicle.getLateralSpeed();
        double yawRate = vehicle.yawRate();
        float yaw = vehicle.getYaw();
        double vSpeed = vehicle.verticalSpeed();

        Location loc = vehicle.getLocation();
        Block under = loc.clone().subtract(0, 0.15 + type.hoverHeight(), 0).getBlock();
        SurfacePhysics.Sample surface = SurfacePhysics.sample(under);
        double grip = Math.max(0.05, vehicle.effTraction() * surface.traction());
        if (input.handbrake()) {
            grip *= type.handbrakeGripScale();
        }

        boolean canThrottle = vehicle.hasFuelForThrottle();
        double throttle = canThrottle ? input.throttle() : Math.min(0, input.throttle());
        if (input.boost() && throttle > 0 && canThrottle) {
            throttle = Math.min(1.0, throttle * 1.2 * vehicle.effBoostMul());
        }

        // Engine curve — acceleration falls off toward max speed (not flat vanilla push)
        double maxSpeed = vehicle.effMaxSpeed();
        double speedRatio = Math.min(1.0, Math.abs(speed) / Math.max(0.01, maxSpeed));
        double engine = vehicle.effAcceleration() * (1.0 - 0.55 * speedRatio * speedRatio);

        if (input.brake() || input.handbrake()) {
            double brake = vehicle.effBrake() * (input.handbrake() ? 1.35 : 1.0) * grip;
            if (speed > 0) {
                speed = Math.max(0, speed - brake);
            } else if (speed < 0) {
                speed = Math.min(0, speed + brake);
            }
        } else if (throttle > 0) {
            speed += engine * throttle * grip;
        } else if (throttle < 0) {
            speed -= engine * type.reverseRatio() * (-throttle) * grip;
        }

        // Air drag + rolling resistance (surface-scaled)
        double resist = type.drag() + vehicle.effRolling() * surface.rolling();
        if (Math.abs(speed) > 1e-4) {
            if (speed > 0) {
                speed = Math.max(0, speed - resist);
            } else {
                speed = Math.min(0, speed + resist);
            }
        }

        // Slope grade force — push downhill / resist uphill
        double grade = sampleGrade(loc, yaw, type);
        speed += grade * type.slopeGrip() * vehicle.effSlopeGrip() * grip;

        double max = maxSpeed * (0.85 + 0.15 * surface.traction());
        double min = -maxSpeed * type.reverseRatio();
        speed = Math.max(min, Math.min(max, speed));

        // Yaw inertia — steer targets a rate; rate integrates into heading
        double steerFactor = Math.min(1.0, Math.abs(speed) / Math.max(0.05, maxSpeed * 0.3));
        double targetYawRate = 0;
        if (Math.abs(input.steer()) > 0.01 && Math.abs(speed) > 0.02) {
            targetYawRate = vehicle.effTurnRate() * input.steer() * steerFactor;
            if (speed < 0) {
                targetYawRate = -targetYawRate;
            }
            if (input.handbrake()) {
                targetYawRate *= 1.4;
            }
        }
        double inertia = Math.max(0.08, type.yawInertia());
        yawRate += (targetYawRate - yawRate) * Math.min(1.0, 1.0 / inertia);
        yawRate *= (0.92 + 0.06 * grip); // damping
        yaw = wrapYaw(yaw + (float) yawRate);

        // Lateral slip from yaw while moving (drift) — not in vanilla
        double slipInject = Math.toRadians(yawRate) * speed * 0.55;
        lateral += slipInject;
        double latKill = vehicle.effLateralGrip() * grip;
        if (input.handbrake()) {
            latKill *= type.handbrakeGripScale();
        }
        lateral *= Math.max(0, 1.0 - latKill);
        if (Math.abs(lateral) < 0.001) {
            lateral = 0;
        }
        // Cap wild slip
        double latCap = maxSpeed * 0.65;
        lateral = Math.max(-latCap, Math.min(latCap, lateral));

        Vector extra = input.extraForce();
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);
        // Local forward/right → world
        double dx = sin * speed + cos * lateral + extra.getX();
        double dz = cos * speed - sin * lateral + extra.getZ();

        boolean grounded = under.getType().isSolid()
                || (type.hoverHeight() > 0 && hasGroundBelow(loc, type.hoverHeight() + 1.5));

        if (type.hoverHeight() > 0) {
            double targetY = groundY(loc) + type.hoverHeight();
            double err = targetY - loc.getY();
            vSpeed = err * 0.35;
            if (Math.abs(err) < 0.05) {
                vSpeed = 0;
            }
        } else if (!grounded) {
            vSpeed -= type.gravity();
            vSpeed = Math.max(-0.9, vSpeed);
            // Air: less lateral grip
            lateral *= 0.97;
        } else {
            vSpeed = Math.min(0, vSpeed);
            double gy = groundY(loc) + vehicle.effRideHeight();
            if (loc.getY() < gy) {
                loc.setY(gy);
            } else if (loc.getY() > gy + 0.15 && grounded) {
                // Settle onto tires after jumps / lift changes
                loc.setY(Math.max(gy, loc.getY() - 0.08));
            }
        }

        vSpeed += extra.getY();
        Location next = loc.clone().add(dx, vSpeed, dz);

        Block hit = collisionBlock(loc, next, type);
        if (hit != null) {
            Vector impact = new Vector(dx, vSpeed, dz);
            VehicleCollideEvent event = new VehicleCollideEvent(vehicle, hit, impact);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                double impactSpeed = Math.hypot(speed, lateral);
                speed *= -0.2;
                lateral *= -0.35;
                yawRate *= 0.5;
                vSpeed = Math.min(vSpeed, 0);
                next = loc.clone();
                if (config.damageEnabled() && type.usesDamage() && impactSpeed > 0.2) {
                    vehicle.damage(impactSpeed * type.collisionDamageScale(), "collision");
                    if (!vehicle.isAlive()) {
                        return;
                    }
                }
            }
        }

        if (canThrottle && Math.abs(throttle) > 0.01) {
            vehicle.consumeFuel(type.fuelPerTick() * Math.abs(throttle));
        }

        vehicle.setSpeed(speed);
        vehicle.setLateralSpeed(lateral);
        vehicle.setYawRate(yawRate);
        vehicle.setYaw(yaw);
        vehicle.setVerticalSpeed(vSpeed);
        vehicle.getChassis().teleport(next);
        vehicle.syncTransforms();
    }

    /** Approximate grade along heading: positive = downhill. */
    private static double sampleGrade(Location loc, float yaw, VehicleType type) {
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);
        double probe = Math.max(1.0, type.length() * 0.45);
        Location ahead = loc.clone().add(sin * probe, 0.5, cos * probe);
        Location behind = loc.clone().add(-sin * probe, 0.5, -cos * probe);
        double ya = groundY(ahead);
        double yb = groundY(behind);
        return (yb - ya) / (probe * 2.0);
    }

    private static float wrapYaw(float yaw) {
        yaw %= 360f;
        if (yaw < -180f) {
            yaw += 360f;
        }
        if (yaw > 180f) {
            yaw -= 360f;
        }
        return yaw;
    }

    private static boolean hasGroundBelow(Location loc, double maxDrop) {
        Location c = loc.clone();
        for (double y = 0; y <= maxDrop; y += 0.25) {
            if (c.clone().subtract(0, y, 0).getBlock().getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static double groundY(Location loc) {
        Location c = loc.clone();
        for (int i = 0; i < 48; i++) {
            Block b = c.getBlock();
            if (b.getType().isSolid()) {
                return b.getY() + 1.0;
            }
            c.subtract(0, 0.25, 0);
            if (c.getY() < loc.getWorld().getMinHeight()) {
                break;
            }
        }
        return loc.getY();
    }

    private static Block collisionBlock(Location from, Location to, VehicleType type) {
        Vector delta = to.toVector().subtract(from.toVector());
        delta.setY(0);
        if (delta.lengthSquared() < 1e-8) {
            return null;
        }
        Vector step = delta.clone().normalize().multiply(0.25);
        Location cursor = from.clone().add(0, type.height() * 0.45, 0);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.25));
        for (int i = 0; i < steps; i++) {
            cursor.add(step);
            Block b = cursor.getBlock();
            Material m = b.getType();
            if (m.isSolid() && !m.name().contains("LEAVES")) {
                return b;
            }
            Vector right = new Vector(-step.getZ(), 0, step.getX());
            if (right.lengthSquared() > 1e-8) {
                right.normalize().multiply(type.width() * 0.4);
                Block leftB = cursor.clone().add(right).getBlock();
                if (leftB.getType().isSolid()) {
                    return leftB;
                }
                Block rightB = cursor.clone().subtract(right).getBlock();
                if (rightB.getType().isSolid()) {
                    return rightB;
                }
            }
        }
        return null;
    }
}
