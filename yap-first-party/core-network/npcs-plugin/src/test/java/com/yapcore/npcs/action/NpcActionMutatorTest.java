package com.yapcore.npcs.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcActionMutatorTest {

    @Test
    void replacesShopKeepsWarp() {
        String next = NpcActionMutator.replaceKind("shop:1;warp:spawn", NpcActions.Kind.SHOP, "shop:9");
        assertEquals("warp:spawn;shop:9", next);
        assertEquals(9L, NpcActionMutator.shopId(next).orElse(-1L));
    }

    @Test
    void clearsWarp() {
        String next = NpcActionMutator.replaceKind("warp:spawn;command:say hi", NpcActions.Kind.WARP, null);
        assertEquals("command:say hi", next);
        assertTrue(NpcActionMutator.shopId(next).isEmpty());
    }
}
