package com.yapcore.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimPressurePlateRulesTest {

    @Test
    void deniesMobsOnPlatesWhenConfigured() {
        assertFalse(ClaimPressurePlateRules.allow(true, false, false, true));
    }

    @Test
    void allowsPlayersAlways() {
        assertTrue(ClaimPressurePlateRules.allow(true, false, true, true));
    }

    @Test
    void allowsWhenVanillaToggleOn() {
        assertTrue(ClaimPressurePlateRules.allow(true, true, false, true));
    }

    @Test
    void ignoresNonPlates() {
        assertTrue(ClaimPressurePlateRules.allow(true, false, false, false));
    }

    @Test
    void detectsPressurePlateNames() {
        assertTrue(ClaimPressurePlateRules.isPressurePlate("STONE_PRESSURE_PLATE"));
        assertTrue(ClaimPressurePlateRules.isPressurePlate("LIGHT_WEIGHTED_PRESSURE_PLATE"));
        assertFalse(ClaimPressurePlateRules.isPressurePlate("STONE_BUTTON"));
        assertFalse(ClaimPressurePlateRules.isPressurePlate(null));
    }
}
