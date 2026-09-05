package com.yapcore.dungeons.gen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomGraphBuilderTest {

    @Test
    void seedStable() {
        RoomGraphBuilder builder = new RoomGraphBuilder();
        var a = builder.build(42L, 8);
        var b = builder.build(42L, 8);
        assertEquals(a.rooms().size(), b.rooms().size());
        assertEquals(a.rooms().getFirst().x(), b.rooms().getFirst().x());
        assertEquals(a.rooms().getLast().kind(), RoomGraphBuilder.RoomKind.BOSS);
        assertEquals(a.rooms().getFirst().kind(), RoomGraphBuilder.RoomKind.ENTRANCE);
    }

    @Test
    void corridorsConnect() {
        RoomGraphBuilder.Layout layout = new RoomGraphBuilder().build(99L, 10);
        Set<Integer> touched = new HashSet<>();
        touched.add(0);
        for (var c : layout.corridors()) {
            touched.add(c.fromId());
            touched.add(c.toId());
        }
        assertTrue(touched.size() >= layout.rooms().size() - 1);
    }
}
