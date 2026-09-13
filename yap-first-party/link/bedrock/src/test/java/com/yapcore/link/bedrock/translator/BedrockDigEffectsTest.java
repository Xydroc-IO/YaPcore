package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** BE-BREAK-01: dig LevelSoundEvent extraData must be a block runtime, never -1. */
final class BedrockDigEffectsTest {

    @Test
    void prefersStoneRuntime() {
        assertEquals(42, BedrockDigEffects.resolveDigExtraData(42, 0));
    }

    @Test
    void fallsBackToAirButNeverNegativeOne() {
        assertEquals(7, BedrockDigEffects.resolveDigExtraData(0, 7));
        assertEquals(1, BedrockDigEffects.resolveDigExtraData(0, 0));
        assertNotEquals(-1, BedrockDigEffects.resolveDigExtraData(0, -1));
        assertTrue(BedrockDigEffects.resolveDigExtraData(0, -1) >= 1);
    }
}
