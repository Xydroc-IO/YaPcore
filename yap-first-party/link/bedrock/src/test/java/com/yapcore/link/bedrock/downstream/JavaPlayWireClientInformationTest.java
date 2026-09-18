package com.yapcore.link.bedrock.downstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

/** Ensures Client Information advertises a full Folia view distance (not the old hardcoded 8). */
final class JavaPlayWireClientInformationTest {

    @Test
    void clientInformationEncodesViewDistance32ByDefaultPath() {
        ByteBuf buf = JavaPlayWire.clientInformation(JavaDownstreamClient.DEFAULT_VIEW_DISTANCE);
        try {
            int packetId = McCodec.readVarInt(buf);
            assertEquals(JavaPlayWire.SB_CLIENT_INFORMATION, packetId);
            String locale = McCodec.readString(buf, 16);
            assertEquals("en_US", locale);
            int view = buf.readByte();
            assertEquals(32, view);
        } finally {
            buf.release();
        }
    }

    @Test
    void clientInformationClampsTo32() {
        ByteBuf buf = JavaPlayWire.clientInformation(99);
        try {
            McCodec.readVarInt(buf);
            McCodec.readString(buf, 16);
            assertEquals(32, buf.readByte());
        } finally {
            buf.release();
        }
    }

    @Test
    void clientInformationClampsFloorTo2() {
        ByteBuf buf = JavaPlayWire.clientInformation(0);
        try {
            McCodec.readVarInt(buf);
            McCodec.readString(buf, 16);
            assertEquals(2, buf.readByte());
        } finally {
            buf.release();
        }
    }

    @Test
    void defaultViewDistanceIsNotEight() {
        assertTrue(JavaDownstreamClient.DEFAULT_VIEW_DISTANCE > 8,
                "DEFAULT_VIEW_DISTANCE must exceed the old hardcoded 8 that starved Folia chunks");
        assertEquals(32, JavaDownstreamClient.DEFAULT_VIEW_DISTANCE);
    }
}
