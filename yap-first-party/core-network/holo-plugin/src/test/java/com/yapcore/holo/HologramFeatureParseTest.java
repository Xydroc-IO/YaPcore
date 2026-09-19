package com.yapcore.holo;

import com.yapcore.holo.impl.HologramPages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class HologramFeatureParseTest {

    @Test
    void parsesItemAndAnimLines() {
        assertEquals(HologramLine.Kind.ITEM, HologramLine.parse("#ICON:DIAMOND").kind());
        assertEquals("DIAMOND", HologramLine.parse("#ITEM:DIAMOND").value());
        assertEquals(HologramLine.Kind.ANIM, HologramLine.parse("#ANIM:wave").kind());
        assertEquals("wave", HologramLine.parse("#ANIM:wave").value());
        assertEquals("&6Hello %player_name%", HologramLine.parse("&6Hello %player_name%").serialize());
    }

    @Test
    void parsesClicks() {
        HologramClick next = HologramClick.parse("LEFT:NEXT");
        assertEquals(HologramClick.Side.LEFT, next.side());
        assertEquals(HologramClick.Action.NEXT, next.action());
        HologramClick console = HologramClick.parse("RIGHT:CONSOLE:say hi");
        assertEquals(HologramClick.Action.CONSOLE, console.action());
        assertEquals("say hi", console.value());
        assertEquals("LEFT:PAGE:2", HologramClick.parse("LEFT:PAGE:2").serialize());
        assertNull(HologramClick.parse(""));
    }

    @Test
    void parsesAttach() {
        HologramAttach npc = HologramAttach.parse("npc:shop:0.3");
        assertEquals(HologramAttach.Kind.NPC, npc.kind());
        assertEquals("shop", npc.key());
        assertEquals(0.3, npc.offsetY(), 1e-9);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        HologramAttach player = HologramAttach.parse("player:" + id + ":0.4");
        assertEquals(HologramAttach.Kind.PLAYER, player.kind());
        assertEquals(id, player.uuidKey());
        assertEquals(HologramAttach.Kind.NONE, HologramAttach.parse("none").kind());
    }

    @Test
    void splitsPages() {
        List<List<String>> pages = HologramPages.splitPages("&6A|&7B;;#ICON:DIAMOND");
        assertEquals(2, pages.size());
        assertEquals(List.of("&6A", "&7B"), pages.get(0));
        assertEquals(List.of("#ICON:DIAMOND"), pages.get(1));
    }
}
