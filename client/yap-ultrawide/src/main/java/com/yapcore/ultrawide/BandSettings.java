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
     * Extra hand nudge in view space, on top of the automatic vertical FOV
     * scale. Negative X = pull left (keeps swords on-screen on 32:9).
     * Positive Y = lift further above the hotbar. {@code 0} keeps the vanilla gap.
     */
    public float viewmodelOffsetX = 0.0f;
    public float viewmodelOffsetY = 0.0f;
    /** Multiplier on Hor+ viewmodel scale ({@code >1} = bigger hand on screen). */
    public float viewmodelExtraScale = 1.0f;

    public static BandSettings ultrawide21Defaults() {
        BandSettings s = new BandSettings();
        s.mode = "match_16_9";
        s.targetHorizontalFov = 90.0f;
        // 16:9 at the default slider is ~102° horizontal; 90° drops the edge stretch
        // you still see on a 21:9 panel at that match.
        s.maxHorizontalFov = 90.0f;
        s.fovScale = 1.0f;
        s.minVerticalFov = 40.0f;
        s.viewmodelOffsetX = 0.0f;
        s.viewmodelOffsetY = 0.0f;
        s.viewmodelExtraScale = 1.05f;
        return s;
    }

    public static BandSettings superwide32Defaults() {
        BandSettings s = new BandSettings();
        // Match 21:9, then cap. Uncapped match_21_9 is ~117° horizontal (still fisheye).
        // A 103° cap was ~39° vertical and felt telephoto — 105° is the middle.
        s.mode = "match_21_9";
        s.targetHorizontalFov = 105.0f;
        s.maxHorizontalFov = 105.0f;
        s.fovScale = 1.0f;
        s.minVerticalFov = 38.0f;
        // Dual-4K 32:9 pushes the vanilla bottom-right hand off-frame — pull inward.
        // Height above the hotbar comes from the Y FOV scale, not a translate.
        s.viewmodelOffsetX = -0.08f;
        s.viewmodelOffsetY = 0.0f;
        s.viewmodelExtraScale = 1.22f;
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
