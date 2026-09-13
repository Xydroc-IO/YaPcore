package com.yapcore.tailor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ParityMovementApplierTest {

    @Test
    void presencePayloadMatchesFrozenCatalog() {
        String p = ParityMovementApplier.presencePayload();
        assertTrue(p.startsWith("MOVEMENT|"));
        String[] parts = p.split("\\|");
        assertEquals("0.1", parts[1]);
        assertEquals("1.3", parts[2]);
        assertEquals("0.3", parts[3]);
        assertEquals("0.42", parts[4]);
        assertEquals("0.08", parts[5]);
        assertEquals("0.02", parts[6]);
        assertEquals("0.05", parts[7]);
        assertEquals("5.0", parts[8]);
        assertEquals("3.0", parts[9]);
        assertEquals("band_26_50", parts[11]);
    }
}
