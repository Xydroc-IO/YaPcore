package com.yapcore.link.bedrock.downstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

final class JavaPlayWireCombatTest {

    @Test
    void attackPacketMatchesProtocol26_2() {
        ByteBuf buf = JavaPlayWire.interactAttack(626, false);
        try {
            assertEquals(JavaPlayWire.SB_ATTACK, McCodec.readVarInt(buf));
            assertEquals(1, JavaPlayWire.SB_ATTACK);
            assertEquals(626, McCodec.readVarInt(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void playerInputFlagsByte() {
        ByteBuf buf = JavaPlayWire.playerInput(true, false, true, false, true, false, true);
        try {
            assertEquals(JavaPlayWire.SB_PLAYER_INPUT, McCodec.readVarInt(buf));
            assertEquals(43, JavaPlayWire.SB_PLAYER_INPUT);
            // forward|left|jump|sprint = 1|4|16|64 = 85
            assertEquals(85, buf.readUnsignedByte());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void clientTickEndId() {
        ByteBuf buf = JavaPlayWire.clientTickEnd();
        try {
            assertEquals(JavaPlayWire.SB_CLIENT_TICK_END, McCodec.readVarInt(buf));
            assertEquals(13, JavaPlayWire.SB_CLIENT_TICK_END);
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void attackWireHexNonEmpty() {
        String hex = JavaPlayWire.attackWireHex(42);
        assertTrue(hex.length() >= 4);
        assertTrue(hex.startsWith("01")); // SB_ATTACK varint = 0x01
    }
}
