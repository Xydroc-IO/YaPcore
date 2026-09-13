package com.yapcore.tailor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TailorEmoteCatalogTest {

    @Test
    void frozenCatalogMatchesChassisBand() {
        TailorEmoteCatalog c = TailorEmoteCatalog.get();
        assertEquals(4, c.size());
        assertTrue(c.byId("4c8ae710-df2e-47cd-814d-cc7bf21a3d67").isPresent());
        assertTrue(c.resolve("wave").isPresent());
        assertTrue(c.resolve("Wave").isPresent());
        assertEquals("Wave", c.resolve("wave").orElseThrow().name());
        assertTrue(c.resolve("Follow Me").isPresent());
    }
}
