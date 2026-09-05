package com.yapcore.ultrawide;

/**
 * Converts vertical FOV so horizontal FOV stays consistent on wide aspects.
 *
 * <p>Vanilla Minecraft stores a <em>vertical</em> FOV. On 21:9 / 32:9 that
 * produces a much wider horizontal view (fish-eye). Hor+ here means: pick a
 * target horizontal FOV, then derive the vertical FOV for the real framebuffer.
 *
 * <pre>
 * hfov = 2 * atan(tan(vfov / 2) * aspect)
 * vfov = 2 * atan(tan(hfov / 2) / aspect)
 * </pre>
 */
public final class HorPlus {
    public static final float REFERENCE_16_9 = 16.0f / 9.0f;
    public static final float REFERENCE_21_9 = 21.0f / 9.0f;
    /** Spyglass / zoom mods return a very small VFOV — leave those alone. */
    public static final float ZOOM_PASSTHROUGH_MAX = 20.0f;

    private HorPlus() {
    }

    public static float horizontalFromVertical(float verticalDegrees, float aspect) {
        double halfV = Math.toRadians(verticalDegrees) * 0.5;
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(halfV) * aspect));
    }

    public static float verticalFromHorizontal(float horizontalDegrees, float aspect) {
        if (aspect <= 0.0f) {
            return horizontalDegrees;
        }
        double halfH = Math.toRadians(horizontalDegrees) * 0.5;
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(halfH) / aspect));
    }

    /** Keep the horizontal FOV you would have on {@code referenceAspect}. */
    public static float matchReferenceHorizontal(float vanillaVerticalDegrees, float aspect,
                                                 float referenceAspect) {
        float hfov = horizontalFromVertical(vanillaVerticalDegrees, referenceAspect);
        return verticalFromHorizontal(hfov, aspect);
    }

    public static float match16x9(float vanillaVerticalDegrees, float aspect) {
        return matchReferenceHorizontal(vanillaVerticalDegrees, aspect, REFERENCE_16_9);
    }

    /**
     * Better default for 32:9: match the HFOV a 21:9 panel would have at the
     * same slider (less peripheral stretch than match_16_9 on superultrawide).
     */
    public static float match21x9(float vanillaVerticalDegrees, float aspect) {
        return matchReferenceHorizontal(vanillaVerticalDegrees, aspect, REFERENCE_21_9);
    }

    public static float verticalForTargetHorizontal(float targetHorizontalDegrees, float aspect) {
        return verticalFromHorizontal(targetHorizontalDegrees, aspect);
    }

    /** Clamp resulting VFOV so horizontal FOV never exceeds {@code maxHfov}. */
    public static float clampHorizontal(float verticalDegrees, float aspect, float maxHfov) {
        if (maxHfov <= 0.0f) {
            return verticalDegrees;
        }
        float hfov = horizontalFromVertical(verticalDegrees, aspect);
        if (hfov <= maxHfov) {
            return verticalDegrees;
        }
        return verticalFromHorizontal(maxHfov, aspect);
    }
}
