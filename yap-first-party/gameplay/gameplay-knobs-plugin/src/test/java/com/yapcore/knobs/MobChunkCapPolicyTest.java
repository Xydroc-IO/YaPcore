package com.yapcore.knobs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobChunkCapPolicyTest {

    @Test
    void overLimitWhenAtOrAboveCap() {
        assertFalse(MobChunkCapPolicy.overLimit(0, 8));
        assertFalse(MobChunkCapPolicy.overLimit(7, 8));
        assertTrue(MobChunkCapPolicy.overLimit(8, 8));
        assertTrue(MobChunkCapPolicy.overLimit(9, 8));
    }

    @Test
    void zeroOrNegativeCapDisabled() {
        assertFalse(MobChunkCapPolicy.overLimit(100, 0));
        assertFalse(MobChunkCapPolicy.overLimit(100, -1));
    }

    @Test
    void mobKnobsMaxPerChunkOrZero() {
        KnobsConfig.MobKnobs unset = sample(null);
        assertEquals(0, unset.maxPerChunkOrZero());

        KnobsConfig.MobKnobs zero = sample(0);
        assertEquals(0, zero.maxPerChunkOrZero());

        KnobsConfig.MobKnobs eight = sample(8);
        assertEquals(8, eight.maxPerChunkOrZero());
    }

    private static KnobsConfig.MobKnobs sample(Integer maxPerChunk) {
        return new KnobsConfig.MobKnobs(
                true, false, true, false, 320.0, false, false, "default", 6000,
                true, false, maxPerChunk, Map.of(), true, false, false, false,
                List.of(), KnobsConfig.MobSpecials.empty());
    }
}
