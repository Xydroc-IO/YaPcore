package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RakNetInvalidSplitRecoveryTest {

    @Test
    void invalidSplitCountRecoversPayloadInsteadOfDropping() {
        RakNetReliability.SessionState state = new RakNetReliability.SessionState();
        ByteBuf payload = Unpooled.buffer();
        payload.writeByte(0xfe);
        payload.writeByte(0x01);

        // Craft a frame-set with split flag set but splitCount=0 (invalid).
        ByteBuf frameSet = Unpooled.buffer();
        frameSet.writeByte(0x84);
        frameSet.writeByte(1).writeByte(0).writeByte(0); // dg# 1 LE
        int flags = (RakNetReliability.Reliability.RELIABLE_ORDERED.code << 5) | 0x10;
        frameSet.writeByte(flags);
        frameSet.writeShort(payload.readableBytes() << 3);
        frameSet.writeByte(0).writeByte(0).writeByte(0); // reliable
        frameSet.writeByte(0).writeByte(0).writeByte(0); // order
        frameSet.writeByte(0); // channel
        frameSet.writeInt(0); // splitCount INVALID
        frameSet.writeShort(20); // splitId
        frameSet.writeInt(0); // splitIndex
        frameSet.writeBytes(payload);

        RakNetReliability.DecodedFrameSet decoded =
                RakNetReliability.decodeFrameSetEx(frameSet, state);
        assertEquals(1, decoded.frames().size());
        assertFalse(decoded.frames().get(0).payload().readableBytes() < 10);
        ByteBuf body = decoded.frames().get(0).payload();
        // recovered = split meta (10) + original payload
        assertEquals(0xfe, body.getUnsignedByte(body.readerIndex() + 10));

        frameSet.release();
        payload.release();
        for (var f : decoded.frames()) {
            f.payload().release();
        }
    }
}
