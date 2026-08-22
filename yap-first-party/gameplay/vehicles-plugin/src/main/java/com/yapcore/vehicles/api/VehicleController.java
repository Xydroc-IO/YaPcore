package com.yapcore.vehicles.api;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Maps driver input to throttle / steer / brake forces each tick.
 * Authors implement this for tanks, planes, hovercraft, etc.
 */
@FunctionalInterface
public interface VehicleController {

    /**
     * @param vehicle live instance
     * @param driver  may be null (coast only)
     * @param input   normalized driver intent; mutate freely for this tick
     */
    void apply(Vehicle vehicle, Player driver, ControlInput input);

    /**
     * Default car-like WASD:
     * forward/back = throttle, left/right = steer, jump = brake, sprint = boost, sneak = handbrake.
     */
    VehicleController DEFAULT = (vehicle, driver, input) -> {
        if (driver == null) {
            return;
        }
        var raw = driver.getCurrentInput();
        double throttle = 0;
        if (raw.isForward()) {
            throttle += 1;
        }
        if (raw.isBackward()) {
            throttle -= 1;
        }
        double steer = 0;
        if (raw.isLeft()) {
            steer += 1;
        }
        if (raw.isRight()) {
            steer -= 1;
        }
        input.setThrottle(throttle);
        input.setSteer(steer);
        input.setBrake(raw.isJump());
        input.setBoost(raw.isSprint());
        input.setHandbrake(raw.isSneak());
    };

    /** Mutable per-tick control state produced by a {@link VehicleController}. */
    final class ControlInput {
        private double throttle;
        private double steer;
        private boolean brake;
        private boolean boost;
        private boolean handbrake;
        private Vector extraForce = new Vector();

        public double throttle() {
            return throttle;
        }

        public void setThrottle(double throttle) {
            this.throttle = Math.max(-1, Math.min(1, throttle));
        }

        public double steer() {
            return steer;
        }

        public void setSteer(double steer) {
            this.steer = Math.max(-1, Math.min(1, steer));
        }

        public boolean brake() {
            return brake;
        }

        public void setBrake(boolean brake) {
            this.brake = brake;
        }

        public boolean boost() {
            return boost;
        }

        public void setBoost(boolean boost) {
            this.boost = boost;
        }

        public boolean handbrake() {
            return handbrake;
        }

        public void setHandbrake(boolean handbrake) {
            this.handbrake = handbrake;
        }

        public Vector extraForce() {
            return extraForce.clone();
        }

        public void setExtraForce(Vector extraForce) {
            this.extraForce = extraForce == null ? new Vector() : extraForce.clone();
        }

        public void clear() {
            throttle = 0;
            steer = 0;
            brake = false;
            boost = false;
            handbrake = false;
            extraForce = new Vector();
        }
    }
}
