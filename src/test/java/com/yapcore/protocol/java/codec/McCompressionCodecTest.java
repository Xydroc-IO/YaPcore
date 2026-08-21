package com.yapcore.protocol.java.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McCompressionCodecTest {

    @Test
    void belowThresholdUsesUncompressedMarker() {
        McCompressionCodec.Encoder encoder = new McCompressionCodec.Encoder();
        encoder.setThreshold(256);
        EmbeddedChannel enc = new EmbeddedChannel(encoder);

        byte[] payload = new byte[40];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        assertTrue(enc.writeOutbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf framed = enc.readOutbound();
        assertEquals(0, McCodec.readVarInt(framed));
        byte[] got = new byte[framed.readableBytes()];
        framed.readBytes(got);
        framed.release();
        assertArrayEquals(payload, got);
        enc.finishAndReleaseAll();
    }

    @Test
    void roundTripAboveThresholdCompresses() {
        McCompressionCodec.Encoder encoder = new McCompressionCodec.Encoder();
        McCompressionCodec.Decoder decoder = new McCompressionCodec.Decoder();
        encoder.setThreshold(32);
        decoder.setThreshold(32);
        EmbeddedChannel enc = new EmbeddedChannel(encoder);
        EmbeddedChannel dec = new EmbeddedChannel(decoder);

        byte[] payload = new byte[200];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 17);
        }
        assertTrue(enc.writeOutbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf compressedFrame = enc.readOutbound();

        assertTrue(dec.writeInbound(compressedFrame));
        ByteBuf decoded = dec.readInbound();
        byte[] out = new byte[decoded.readableBytes()];
        decoded.readBytes(out);
        decoded.release();
        assertArrayEquals(payload, out);
        enc.finishAndReleaseAll();
        dec.finishAndReleaseAll();
    }
}
