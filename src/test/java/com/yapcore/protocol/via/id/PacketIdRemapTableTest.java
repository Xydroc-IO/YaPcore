package com.yapcore.protocol.via.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cached {@link PacketIdRemapTable} must match name-scan remap for every known id.
 */
class PacketIdRemapTableTest {

    private static final int PAPER = 776;

    @Test
    void tableMatchesNameScan_774to776() {
        assertTableMatches(PAPER, 774);
        assertTableMatches(774, PAPER);
    }

    @Test
    void tableMatchesNameScan_770to776() {
        // 1.21.5-ish mid band — also used by some bot pins
        assertTableMatches(PAPER, 770);
        assertTableMatches(770, PAPER);
    }

    @Test
    void joinCriticalIds_774() {
        PacketIdRemapTable s2c = PacketIdRemapTable.playS2c(PAPER, 774);
        PacketIdRemapTable c2s = PacketIdRemapTable.playC2s(774, PAPER);
        assertEquals(48, s2c.remap(49)); // login
        assertEquals(43, s2c.remap(44)); // keep_alive
        assertEquals(70, s2c.remap(72)); // position
        assertEquals(44, s2c.remap(45)); // map_chunk
        assertEquals(20, s2c.remap(20)); // set_slot
        assertEquals(30, c2s.remap(29)); // C2S position
        assertEquals(28, c2s.remap(27)); // C2S keep_alive
    }

    private static void assertTableMatches(int fromProto, int toProto) {
        PacketIdDump from = PacketIdDump.forProtocol(fromProto);
        PacketIdDump to = PacketIdDump.forProtocol(toProto);
        assertTrue(from.hasPlay() && to.hasPlay(), "dumps " + fromProto + "→" + toProto);

        PacketIdRemapTable s2c = PacketIdRemapTable.playS2c(from, to);
        Set<Integer> s2cIds = new HashSet<>(from.playS2cNames().values());
        for (int id : s2cIds) {
            assertEquals(
                    PacketIdDump.remapPlayS2c(from, to, id),
                    s2c.remap(id),
                    "S2C " + fromProto + "→" + toProto + " id=" + id);
        }

        PacketIdRemapTable c2s = PacketIdRemapTable.playC2s(from, to);
        Set<Integer> c2sIds = new HashSet<>(from.playC2sNames().values());
        for (int id : c2sIds) {
            assertEquals(
                    PacketIdDump.remapPlayC2s(from, to, id),
                    c2s.remap(id),
                    "C2S " + fromProto + "→" + toProto + " id=" + id);
        }
    }
}
