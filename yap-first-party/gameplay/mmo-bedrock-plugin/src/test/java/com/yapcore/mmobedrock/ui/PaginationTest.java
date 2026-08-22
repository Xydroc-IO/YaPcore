package com.yapcore.mmobedrock.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationTest {

    @Test
    void slicesPages() {
        List<Integer> all = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
        Pagination.Page<Integer> p1 = Pagination.slice(all, 1, 4);
        assertEquals(4, p1.items().size());
        assertEquals(1, p1.page());
        assertFalse(p1.hasPrev());
        assertTrue(p1.hasNext());

        Pagination.Page<Integer> p2 = Pagination.slice(all, 2, 4);
        assertEquals(4, p2.items().get(0).intValue(), 5);
        assertTrue(p2.hasPrev());
        assertTrue(p2.hasNext());

        Pagination.Page<Integer> p3 = Pagination.slice(all, 3, 4);
        assertEquals(1, p3.items().size());
        assertFalse(p3.hasNext());
    }
}
