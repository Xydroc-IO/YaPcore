package com.yapcore.npcs.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcActionMutatorTest {

    @Test
    void replacesShopKeepsWarp() {
        String next = NpcActionMutator.replaceKind("shop:1;warp:mines", NpcActions.Kind.SHOP, "shop:9");
        assertEquals("warp:mines;shop:9", next);
        assertEquals(9L, NpcActionMutator.shopId(next).orElse(-1L));
    }

    @Test
    void clearsWarp() {
        String next = NpcActionMutator.replaceKind("warp:mines;command:say hi", NpcActions.Kind.WARP, null);
        assertEquals("command:say hi", next);
        assertTrue(NpcActionMutator.shopId(next).isEmpty());
    }

    @Test
    void spawnTokenRoundTrips() {
        String next = NpcActionMutator.replaceKind("shop:1", NpcActions.Kind.SPAWN, "spawn");
        assertEquals("shop:1;spawn", next);
        assertTrue(NpcActionMutator.hasSpawn(next));
        assertEquals("shop:1", NpcActionMutator.replaceKind(next, NpcActions.Kind.SPAWN, null));
    }
}
