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
    /** Vanilla first-person hand camera ({@code Camera.BASE_HUD_FOV}). */
    public static final float VANILLA_HUD_FOV = 70.0f;

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

    /**
     * Scale first-person hands so they keep vanilla on-screen size when the
     * HUD camera uses world Hor+ VFOV instead of {@code hudVfov}.
     *
     * <p>NDC size is proportional to {@code 1 / tan(vfov / 2)}. Scaling the
     * viewmodel by {@code tan(world/2) / tan(hud/2)} cancels the zoom so
     * weapons stay on screen while sharing the world frustum (aim match).
     */
    public static float viewmodelScale(float worldVfov, float hudVfov) {
        if (hudVfov <= 1.0f || worldVfov <= 1.0f) {
            return 1.0f;
        }
        if (Math.abs(worldVfov - hudVfov) < 0.05f) {
            return 1.0f;
        }
        double scale = Math.tan(Math.toRadians(worldVfov) * 0.5)
                / Math.tan(Math.toRadians(hudVfov) * 0.5);
        // Allow >1 when world VFOV is wider than vanilla HUD (minVerticalFov floor).
        return (float) Math.max(0.55, Math.min(1.45, scale));
    }

    /**
     * Auto hand nudge for ultrawide: vanilla places the viewmodel bottom-right;
     * extreme aspect maps that offset further off-frame. {@code excess} is
     * {@code aspect / (16/9) - 1} (≈1.0 on 7680×2160).
     */
    public static float autoViewmodelOffsetX(float aspect) {
        float excess = Math.max(0.0f, aspect / REFERENCE_16_9 - 1.0f);
        return -0.28f * excess;
    }

    public static float autoViewmodelOffsetY(float aspect) {
        float excess = Math.max(0.0f, aspect / REFERENCE_16_9 - 1.0f);
        return 0.12f * excess;
    }

    /**
     * Scale view-bob amplitude so on-screen bounce stays similar to 16:9.
     * Hor+ lowers VFOV (zoom); vanilla bob is a fixed translation, so without
     * this the world slides under a stable crosshair and blocks place off-aim.
     */
    public static float bobScale(float appliedVfov, float vanillaVfov) {
        if (vanillaVfov <= 1.0f) {
            return 1.0f;
        }
        float scale = appliedVfov / vanillaVfov;
        return Math.max(0.40f, Math.min(1.0f, scale));
    }
}
