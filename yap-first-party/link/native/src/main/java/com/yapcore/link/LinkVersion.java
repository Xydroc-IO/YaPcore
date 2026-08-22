package com.yapcore.link;

/**
 * Native YaP Link release metadata. Bump {@link #PHASE} when a roadmap phase gate passes.
 * See docs/YAP_LINK_NATIVE.md.
 */
public final class LinkVersion {

    /** Active roadmap phase (0 = foundation). */
    public static final int PHASE = 6;

    public static final String NAME = "YaP Link";
    public static final String VERSION = "0.6.0-phase6";

    private LinkVersion() {
    }

    public static String banner() {
        return NAME + " " + VERSION + " (native, phase " + PHASE + ")";
    }
}
