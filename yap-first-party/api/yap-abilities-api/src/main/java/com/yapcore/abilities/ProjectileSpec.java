package com.yapcore.abilities;

public record ProjectileSpec(
        String entityType,
        double speed,
        int maxTicks,
        String trailParticle,
        int trailCount,
        int trailInterval,
        boolean homing,
        double turnRate,
        double splashRadius,
        int iconCmd,
        boolean hideEntity,
        float displayScale) {

    public ProjectileSpec {
        entityType = entityType == null || entityType.isBlank() ? "SNOWBALL" : entityType;
        speed = speed <= 0 ? 1.2 : speed;
        maxTicks = Math.max(5, maxTicks);
        trailParticle = trailParticle == null ? "" : trailParticle;
        trailCount = Math.max(0, trailCount);
        trailInterval = Math.max(1, trailInterval);
        turnRate = turnRate <= 0 ? 0.15 : turnRate;
        splashRadius = Math.max(0, splashRadius);
        displayScale = displayScale <= 0 ? 0.85f : displayScale;
    }

    /** Legacy constructor without homing/splash/icon/display. */
    public ProjectileSpec(
            String entityType,
            double speed,
            int maxTicks,
            String trailParticle,
            int trailCount,
            int trailInterval) {
        this(entityType, speed, maxTicks, trailParticle, trailCount, trailInterval,
                false, 0.15, 0, 0, true, 0.85f);
    }

    /** M7 constructor without hide/scale. */
    public ProjectileSpec(
            String entityType,
            double speed,
            int maxTicks,
            String trailParticle,
            int trailCount,
            int trailInterval,
            boolean homing,
            double turnRate,
            double splashRadius,
            int iconCmd) {
        this(entityType, speed, maxTicks, trailParticle, trailCount, trailInterval,
                homing, turnRate, splashRadius, iconCmd, true, 0.85f);
    }

    public boolean hasTrail() {
        return !trailParticle.isBlank() && trailCount > 0;
    }

    public boolean isHoming() {
        return homing;
    }

    public boolean hasSplash() {
        return splashRadius > 0;
    }
}
