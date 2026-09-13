package com.yapcore.link.bedrock.downstream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaPlayWireAttackTest {

    @Test
    void attackWireIsId1PlusEntityVarIntOnly() {
        String hex = JavaPlayWire.attackWireHex(626);
        // SB_ATTACK=1, entity 626 = 0x01 + varint 626 (0xf2 0x04)
        assertTrue(hex.startsWith("01"), "hex=" + hex);
        assertEquals(JavaPlayWire.SB_ATTACK, 1);
        assertTrue(hex.length() <= 8, "attack should be tiny, hex=" + hex);
    }
}
