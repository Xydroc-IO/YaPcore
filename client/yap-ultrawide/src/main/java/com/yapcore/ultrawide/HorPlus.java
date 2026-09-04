package com.yapcore.ultrawide;

/**
 * Converts vertical FOV so horizontal FOV stays consistent on wide aspects.
 *
 * <p>Vanilla Minecraft stores a <em>vertical</em> FOV. On 21:9 / 32:9 that
 * produces a much wider horizontal view (fish-eye). Hor+ here means: take the
 * horizontal FOV the player would have on 16:9 at that slider value, then
 * derive the vertical FOV for the real framebuffer.
 *
 * <pre>
 * hfov = 2 * atan(tan(vfov / 2) * (refWidth / refHeight))
 * vfov = 2 * atan(tan(hfov / 2) * (height / width))
 * </pre>
 */
public final class HorPlus {
    public static final float REFERENCE_ASPECT = 16.0f / 9.0f;
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

    /**
     * Keep the 16:9-equivalent horizontal FOV, then solve vertical FOV for
     * {@code aspect} ({@code width / height}).
     */
    public static float matchReferenceHorizontal(float vanillaVerticalDegrees, float aspect) {
        float hfov = horizontalFromVertical(vanillaVerticalDegrees, REFERENCE_ASPECT);
        return verticalFromHorizontal(hfov, aspect);
    }

    public static float verticalForTargetHorizontal(float targetHorizontalDegrees, float aspect) {
        return verticalFromHorizontal(targetHorizontalDegrees, aspect);
    }
}
