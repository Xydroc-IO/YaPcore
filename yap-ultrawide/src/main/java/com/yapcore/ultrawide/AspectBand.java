package com.yapcore.ultrawide;

/**
 * Classifies the framebuffer aspect. 21:9 (UWQHD etc.) and 32:9 (super-ultrawide)
 * are first-class; 16:9 stays on vanilla FOV.
 */
public enum AspectBand {
    STANDARD,
    ULTRAWIDE_21_9,
    SUPERWIDE_32_9;

    /** Inclusive lower bound for 21:9-class (covers 2560x1080, 3440x1440, 3840x1600). */
    public static final float ULTRAWIDE_MIN = 1.90f;
    /** Inclusive lower bound for 32:9-class (3840x1080, 5120x1440). */
    public static final float SUPERWIDE_MIN = 2.80f;

    public static AspectBand of(float aspect) {
        if (aspect >= SUPERWIDE_MIN) {
            return SUPERWIDE_32_9;
        }
        if (aspect >= ULTRAWIDE_MIN) {
            return ULTRAWIDE_21_9;
        }
        return STANDARD;
    }

    public boolean ultrawide() {
        return this != STANDARD;
    }
}
