package com.yapcore.playerdata.bag;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackpackPagesTest {

    @Test
    void defaultPagesWhenNoExtraNodes() {
        assertEquals(1, BackpackPages.resolve(node -> false, 1, 5));
        assertEquals(2, BackpackPages.resolve(node -> false, 2, 5));
    }

    @Test
    void highestExplicitPageWins() {
        Set<String> have = Set.of("yapdata.bag.pages.5", "yapdata.bag.pages.7");
        assertEquals(7, BackpackPages.resolve(have::contains, 3, 9));
    }

    @Test
    void starAndAdminUseMax() {
        assertEquals(5, BackpackPages.resolve("yapdata.bag.pages.*"::equals, 1, 5));
        assertEquals(5, BackpackPages.resolve("yapdata.admin"::equals, 1, 5));
    }

    @Test
    void neverExceedsConfiguredMax() {
        assertEquals(4, BackpackPages.resolve(node -> true, 9, 4));
        assertEquals(2, BackpackPages.clampPage(9, 2));
        assertEquals(1, BackpackPages.clampPage(0, 3));
    }
}
