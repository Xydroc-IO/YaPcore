package com.yapcore.holo;

/** Vertical packing for hologram lines. Index 0 is the top line. */
public final class HologramLineLayout {

    private HologramLineLayout() {
    }

    public static double yOffset(int lineIndex, int lineCount, double spacing) {
        if (lineCount <= 0) {
            return 0.0;
        }
        double space = Math.max(0.01, spacing);
        double top = ((lineCount - 1) * space) / 2.0;
        return top - (lineIndex * space);
    }
}
