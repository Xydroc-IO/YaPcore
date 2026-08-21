package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RakNetFrameSetRangeTest {

    @Test
    void acceptsFullDataPacketIdRange() {
        for (int id = 0x80; id <= 0x8f; id++) {
            assertTrue(RakNetReliability.isFrameSet(id), "id=0x" + Integer.toHexString(id));
        }
        assertTrue(!RakNetReliability.isFrameSet(0x7f));
        assertTrue(!RakNetReliability.isFrameSet(0x90));
    }

    @Test
    void decodesReliableOrderedGameBatch() {
        RakNetReliability.SessionState state = new RakNetReliability.SessionState();
        ByteBuf game = Unpooled.buffer();
        game.writeByte(0xfe);
        game.writeByte(0x01); // stub body
        ByteBuf frameSet = RakNetReliability.wrapFrameSet(state,
                RakNetReliability.reliableOrdered(state, game));
        // Force datagram id into high nibble range (0x8e)
        frameSet.setByte(0, 0x8e);
        RakNetReliability.DecodedFrameSet decoded = RakNetReliability.decodeFrameSetEx(frameSet, state);
        assertEquals(1, decoded.frames().size());
        assertTrue(decoded.madeProgress());
        assertEquals(0xfe, decoded.frames().get(0).payload().getUnsignedByte(
                decoded.frames().get(0).payload().readerIndex()));
        frameSet.release();
        for (var f : decoded.frames()) {
            f.payload().release();
        }
    }
}
