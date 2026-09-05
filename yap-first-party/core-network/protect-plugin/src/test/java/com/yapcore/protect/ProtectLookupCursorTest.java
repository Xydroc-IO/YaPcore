package com.yapcore.protect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectLookupCursorTest {

    @Test
    void encodesAndDecodes() {
        ProtectLookupCursor cursor = new ProtectLookupCursor(1_700_000_000_000L, 42L);
        assertEquals("1700000000000:42", cursor.encode());
        ProtectLookupCursor roundTrip = ProtectLookupCursor.decode(cursor.encode()).orElseThrow();
        assertEquals(cursor, roundTrip);
    }

    @Test
    void pageDetectsHasMore() {
        var rows = java.util.List.of(
                row(3, 300),
                row(2, 200),
                row(1, 100));
        // pageSize 2 with 3 fetched → has more
        ProtectLookupPage page = ProtectLookupPage.of(rows, 2);
        assertTrue(page.hasMore());
        assertEquals(2, page.rows().size());
        assertEquals(new ProtectLookupCursor(200, 2), page.nextCursor());
        assertFalse(ProtectLookupPage.of(rows.subList(0, 2), 2).hasMore());
    }

    private static BlockChangeRecord row(long id, long epoch) {
        return new BlockChangeRecord(id, "lobby", null, "#Nature", "world", 0, 64, 0,
                "EXPLOSION", "STONE", "AIR", epoch, false);
    }
}
