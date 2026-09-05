package com.yapcore.npcs.action;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcActionsTest {

    @Test
    void parsesShopWarpAndCommandChain() {
        List<NpcActions.Action> actions = NpcActions.parse("shop:12; warp:spawn; command:say hi {player}");
        assertEquals(3, actions.size());
        assertEquals(NpcActions.Kind.SHOP, actions.get(0).kind());
        assertEquals("12", actions.get(0).value());
        assertEquals(NpcActions.Kind.WARP, actions.get(1).kind());
        assertEquals("spawn", actions.get(1).value());
        assertEquals(NpcActions.Kind.COMMAND, actions.get(2).kind());
    }

    @Test
    void acceptsTraderAliasAndPlayerCmd() {
        List<NpcActions.Action> actions = NpcActions.parse("trader:3; player:kit starter");
        assertEquals(2, actions.size());
        assertEquals(NpcActions.Kind.SHOP, actions.get(0).kind());
        assertEquals(NpcActions.Kind.PLAYER, actions.get(1).kind());
        assertEquals("kit starter", actions.get(1).value());
    }

    @Test
    void blankAndUnknownAreIgnored() {
        assertTrue(NpcActions.parse(null).isEmpty());
        assertTrue(NpcActions.parse("  ").isEmpty());
        assertTrue(NpcActions.parse("noop:thing").isEmpty());
        assertTrue(NpcActions.parse("shop:").isEmpty());
    }
}
