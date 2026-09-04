package com.yapcore.abilities;

/**
 * Projectile cosmetics + flight. Path/trail/impact fields are V1 VFX extensions;
 * legacy constructors keep older packs compiling.
 */
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
        float displayScale,
        String path,
        double arcHeight,
        String trailStyle,
        double trailFalloff,
        boolean impactShake,
        double shakePower) {

    public ProjectileSpec {
        entityType = entityType == null || entityType.isBlank() ? "SNOWBALL" : entityType;
        speed = speed <= 0 ? 1.2 : speed;
        maxTicks = Math.max(5, maxTicks);
        trailParticle = trailParticle == null ? "" : trailParticle;
        trailCount = Math.max(0, trailCount);
        trailInterval = Math.max(1, trailInterval);
        turnRate = turnRate <= 0 ? 0.15 : turnRate;
        splashRadius = Math.max(0, splashRadius);
        displayScale = displayScale <= 0 ? 1.15f : displayScale;
        path = path == null || path.isBlank() ? "straight" : path.trim().toLowerCase();
        arcHeight = Math.max(0, arcHeight);
        trailStyle = trailStyle == null || trailStyle.isBlank() ? "burst" : trailStyle.trim().toLowerCase();
        trailFalloff = Math.max(0, Math.min(1, trailFalloff));
        shakePower = shakePower <= 0 ? 0.14 : shakePower;
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
                false, 0.15, 0, 0, true, 0.85f,
                "straight", 0, "burst", 0, false, 0.14);
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
                homing, turnRate, splashRadius, iconCmd, true, 0.85f,
                "straight", 0, "burst", 0, false, 0.14);
    }

    /** M7+ display constructor without V1 path/trail/impact. */
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
            int iconCmd,
            boolean hideEntity,
            float displayScale) {
        this(entityType, speed, maxTicks, trailParticle, trailCount, trailInterval,
                homing, turnRate, splashRadius, iconCmd, hideEntity, displayScale,
                "straight", 0, "burst", 0, false, 0.14);
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

    public boolean isArc() {
        return "arc".equals(path) || "curve".equals(path) || "bezier".equals(path);
    }

    public boolean hasMotionTrail() {
        return "motion".equals(trailStyle) || "ribbon".equals(trailStyle);
    }
}
