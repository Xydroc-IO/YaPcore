package com.yapcore.abilities.book;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityBookPaginationTest {

    @Test
    void slicesPages() {
        List<Integer> all = List.of(1, 2, 3, 4, 5, 6, 7);
        AbilityBookPagination.Page<Integer> p1 = AbilityBookPagination.slice(all, 1, 3);
        assertEquals(3, p1.items().size());
        assertEquals(1, p1.page());
        assertEquals(3, p1.totalPages());
        assertFalse(p1.hasPrev());
        assertTrue(p1.hasNext());

        AbilityBookPagination.Page<Integer> p3 = AbilityBookPagination.slice(all, 3, 3);
        assertEquals(List.of(7), p3.items());
        assertTrue(p3.hasPrev());
        assertFalse(p3.hasNext());
    }
}
