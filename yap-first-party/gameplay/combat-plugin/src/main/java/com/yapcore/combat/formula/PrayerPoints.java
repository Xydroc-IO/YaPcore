package com.yapcore.combat.formula;

/** Prayer point pool sizing (RS-style: max points = prayer level). */
public final class PrayerPoints {

    private PrayerPoints() {
    }

    public static int maxPoints(int prayerLevel) {
        return Math.max(0, prayerLevel);
    }

    public static int clamp(int current, int max) {
        return Math.max(0, Math.min(max, current));
    }
}
