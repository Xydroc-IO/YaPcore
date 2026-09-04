package com.yapcore.protect.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeTypeTest {

    @Test
    void roundTripsWireNames() {
        for (ChangeType type : ChangeType.values()) {
            assertEquals(type, ChangeType.valueOf(type.name()));
        }
        assertEquals(5, ChangeType.values().length);
    }

    @Test
    void rejectsUnknownWireName() {
        assertThrows(IllegalArgumentException.class, () -> ChangeType.valueOf("NOT_A_CHANGE"));
    }
}
