package com.yapcore.combat.formula;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrayerPointsTest {

    @Test
    void maxPointsEqualsPrayerLevel() {
        assertEquals(0, PrayerPoints.maxPoints(0));
        assertEquals(43, PrayerPoints.maxPoints(43));
        assertEquals(99, PrayerPoints.maxPoints(99));
    }

    @Test
    void clampKeepsWithinBounds() {
        assertEquals(5, PrayerPoints.clamp(5, 10));
        assertEquals(0, PrayerPoints.clamp(-3, 10));
        assertEquals(10, PrayerPoints.clamp(99, 10));
    }
}
