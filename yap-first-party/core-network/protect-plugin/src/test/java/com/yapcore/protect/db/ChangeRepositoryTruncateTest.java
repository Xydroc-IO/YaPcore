package com.yapcore.protect.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeRepositoryTruncateTest {

    @Test
    void truncateNullAndShort() {
        assertEquals("", ChangeRepository.truncate(null));
        assertEquals("stone", ChangeRepository.truncate("stone"));
    }

    @Test
    void truncateCapsLongPayloads() {
        String big = "x".repeat(70_000);
        String out = ChangeRepository.truncate(big);
        assertEquals(65_535, out.length());
    }
}
