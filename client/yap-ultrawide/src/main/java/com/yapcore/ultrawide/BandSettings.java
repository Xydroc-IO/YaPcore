package com.yapcore.ultrawide;

/**
 * Per-aspect-band Hor+ settings (21:9 ultrawide vs 32:9 super-ultrawide).
 */
public final class BandSettings {
    /**
     * {@code match_16_9} — same HFOV as 16:9 at the vanilla slider.
     * {@code match_21_9} — same HFOV as 21:9 (tighter; good on 32:9).
     * {@code fixed_hfov} — lock {@link #targetHorizontalFov}.
     */
    public String mode = "match_16_9";
    /** Used when {@code mode} is {@code fixed_hfov}. */
    public float targetHorizontalFov = 100.0f;
    /**
     * Hard cap on horizontal FOV after Hor+ (degrees). {@code 0} = no cap.
     */
    public float maxHorizontalFov = 100.0f;
    /** Extra scale on computed VFOV (&lt;1 = tighter / less edge stretch). */
    public float fovScale = 1.0f;
    /**
     * Floor on applied vertical FOV so Hor+ never telephotos the world
     * (hand + blocks vanish). {@code 0} = no floor. Spyglass still bypasses Hor+.
     */
    public float minVerticalFov = 0.0f;
    /**
     * Unused by the renderer. Kept so older configs still load.
     * Hands stay on the vanilla HUD camera.
     */
    public float viewmodelOffsetX = 0.0f;
    public float viewmodelOffsetY = 0.0f;
    /** Unused by the renderer. Kept so older configs still load. */
    public float viewmodelExtraScale = 1.0f;

    public static BandSettings ultrawide21Defaults() {
        BandSettings s = new BandSettings();
        // Slider in, same horizontal FOV a 16:9 panel would have. No cap, no floor.
        s.mode = "match_16_9";
        s.targetHorizontalFov = 100.0f;
        s.maxHorizontalFov = 0.0f;
        s.fovScale = 1.0f;
        s.minVerticalFov = 0.0f;
        s.viewmodelOffsetX = 0.0f;
        s.viewmodelOffsetY = 0.0f;
        s.viewmodelExtraScale = 1.0f;
        return s;
    }

    public static BandSettings superwide32Defaults() {
        BandSettings s = new BandSettings();
        // Same idea as 21:9, matched to a 21:9 panel so 32:9 is not telephoto.
        // The Minecraft FOV slider still scales this. No cap.
        s.mode = "match_21_9";
        s.targetHorizontalFov = 110.0f;
        s.maxHorizontalFov = 0.0f;
        s.fovScale = 1.0f;
        s.minVerticalFov = 0.0f;
        s.viewmodelOffsetX = 0.0f;
        s.viewmodelOffsetY = 0.0f;
        s.viewmodelExtraScale = 1.0f;
        return s;
    }

    void normalize() {
        if (mode == null || !(mode.equals("match_16_9") || mode.equals("match_21_9")
                || mode.equals("fixed_hfov"))) {
            mode = "match_16_9";
        }
        targetHorizontalFov = clamp(targetHorizontalFov, 30.0f, 150.0f);
        maxHorizontalFov = clamp(maxHorizontalFov, 0.0f, 160.0f);
        fovScale = clamp(fovScale, 0.70f, 1.15f);
        minVerticalFov = clamp(minVerticalFov, 0.0f, 90.0f);
        viewmodelOffsetX = clamp(viewmodelOffsetX, -1.0f, 1.0f);
        viewmodelOffsetY = clamp(viewmodelOffsetY, -1.0f, 1.0f);
        viewmodelExtraScale = clamp(viewmodelExtraScale, 0.50f, 2.0f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public boolean match16x9() {
        return "match_16_9".equals(mode);
    }

    public boolean match21x9() {
        return "match_21_9".equals(mode);
    }
}
