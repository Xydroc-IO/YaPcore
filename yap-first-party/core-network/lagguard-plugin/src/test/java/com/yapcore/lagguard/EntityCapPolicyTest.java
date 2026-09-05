package com.yapcore.lagguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityCapPolicyTest {

    @Test
    void classifyPrefersItemThenProjectileThenMob() {
        assertEquals(EntityCapCategory.ITEMS, EntityCapCategory.classify(true, true, true));
        assertEquals(EntityCapCategory.PROJECTILES, EntityCapCategory.classify(false, true, true));
        assertEquals(EntityCapCategory.MOBS, EntityCapCategory.classify(false, false, true));
        assertEquals(EntityCapCategory.OTHER, EntityCapCategory.classify(false, false, false));
    }

    @Test
    void overLimitRequiresPositiveCapAndAtLeastLimitCount() {
        assertFalse(EntityCapPolicy.overLimit(100, 0));
        assertFalse(EntityCapPolicy.overLimit(100, -1));
        assertFalse(EntityCapPolicy.overLimit(5, 10));
        assertTrue(EntityCapPolicy.overLimit(10, 10));
        assertTrue(EntityCapPolicy.overLimit(11, 10));
    }

    @Test
    void limitForMapsCategories() {
        assertEquals(40, EntityCapPolicy.limitFor(EntityCapCategory.ITEMS, 40, 48, 24));
        assertEquals(48, EntityCapPolicy.limitFor(EntityCapCategory.MOBS, 40, 48, 24));
        assertEquals(24, EntityCapPolicy.limitFor(EntityCapCategory.PROJECTILES, 40, 48, 24));
        assertEquals(0, EntityCapPolicy.limitFor(EntityCapCategory.OTHER, 40, 48, 24));
    }
}
