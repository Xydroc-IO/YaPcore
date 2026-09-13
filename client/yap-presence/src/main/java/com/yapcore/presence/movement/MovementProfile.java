package com.yapcore.presence.movement;

/**
 * Bedrock catalog movement profile pushed over {@code yap:presence}.
 */
public final class MovementProfile {

    public final double speed;
    public final double sprintMultiplier;
    public final double sneakMultiplier;
    public final double jumpImpulse;
    public final double gravity;
    public final double drag;
    public final double flySpeed;
    public final double reachBlock;
    public final double reachEntity;
    public final boolean faceAssist;
    public final String band;

    public MovementProfile(
            double speed,
            double sprintMultiplier,
            double sneakMultiplier,
            double jumpImpulse,
            double gravity,
            double drag,
            double flySpeed,
            double reachBlock,
            double reachEntity,
            boolean faceAssist,
            String band) {
        this.speed = speed;
        this.sprintMultiplier = sprintMultiplier;
        this.sneakMultiplier = sneakMultiplier;
        this.jumpImpulse = jumpImpulse;
        this.gravity = gravity;
        this.drag = drag;
        this.flySpeed = flySpeed;
        this.reachBlock = reachBlock;
        this.reachEntity = reachEntity;
        this.faceAssist = faceAssist;
        this.band = band == null ? "" : band;
    }

    /** Frozen band_26_50 defaults (match chassis catalog). */
    public static MovementProfile catalogDefaults() {
        return new MovementProfile(0.1, 1.3, 0.3, 0.42, 0.08, 0.02, 0.05, 5.0, 3.0, true, "band_26_50");
    }

    /**
     * Parse {@code MOVEMENT|speed|sprint|sneak|jump|gravity|drag|fly|reachB|reachE|face|band}.
     */
    public static MovementProfile parse(String text) {
        if (text == null || !text.regionMatches(true, 0, "MOVEMENT|", 0, 9)) {
            return catalogDefaults();
        }
        String[] p = text.split("\\|", -1);
        if (p.length < 11) {
            return catalogDefaults();
        }
        try {
            return new MovementProfile(
                    Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]),
                    Double.parseDouble(p[4]),
                    Double.parseDouble(p[5]),
                    Double.parseDouble(p[6]),
                    Double.parseDouble(p[7]),
                    Double.parseDouble(p[8]),
                    Double.parseDouble(p[9]),
                    "1".equals(p[10].trim()) || "true".equalsIgnoreCase(p[10].trim()),
                    p.length > 11 ? p[11] : "band_26_50");
        } catch (Exception e) {
            return catalogDefaults();
        }
    }
}
