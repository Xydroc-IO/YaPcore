package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Minecraft JE zlib — inbound {@link Decoder}; outbound body wrap via {@link #wrapOutbound}.
 * Do not stack {@link Encoder} before a frame encoder; use {@link McOutboundPacketEncoder}.
 */
public final class McCompressionCodec {

    private static final int MAX_UNCOMPRESSED = 8 * 1024 * 1024;

    private McCompressionCodec() {
    }

    /**
     * Post-Set-Compression body: VarInt(dataLength) + data (0 = uncompressed).
     * Consumes and releases {@code packet}.
     */
    public static ByteBuf wrapOutbound(
            ByteBufAllocator alloc,
            ByteBuf packet,
            int threshold,
            Deflater deflater,
            byte[] encodeBuf) {
        int readable = packet.readableBytes();
        if (readable <= 0) {
            packet.release();
            return alloc.buffer(0);
        }
        if (readable < threshold) {
            ByteBuf out = alloc.buffer(readable + 5);
            McCodec.writeVarInt(out, 0);
            out.writeBytes(packet, packet.readerIndex(), readable);
            packet.release();
            return out;
        }
        byte[] input = new byte[readable];
        packet.getBytes(packet.readerIndex(), input);
        packet.release();
        deflater.reset();
        deflater.setInput(input);
        deflater.finish();
        ByteBuf out = alloc.buffer(readable + 5);
        McCodec.writeVarInt(out, readable);
        while (!deflater.finished()) {
            int n = deflater.deflate(encodeBuf);
            if (n <= 0) {
                break;
            }
            out.writeBytes(encodeBuf, 0, n);
        }
        if (!deflater.finished()) {
            out.release();
            throw new EncoderException("zlib deflate did not finish");
        }
        return out;
    }

    public static final class Decoder extends MessageToMessageDecoder<ByteBuf> {
        private volatile int threshold = -1;
        private final Inflater inflater = new Inflater();

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
            if (threshold < 0) {
                out.add(msg.retain());
                return;
            }
            if (!msg.isReadable()) {
                return;
            }
            int dataLength = McCodec.readVarInt(msg);
            if (dataLength == 0) {
                out.add(msg.readRetainedSlice(msg.readableBytes()));
                return;
            }
            if (dataLength < threshold) {
                throw new DecoderException("Badly compressed packet size " + dataLength);
            }
            if (dataLength > MAX_UNCOMPRESSED) {
                throw new DecoderException("Compressed packet too large " + dataLength);
            }
            byte[] compressed = new byte[msg.readableBytes()];
            msg.readBytes(compressed);
            inflater.reset();
            inflater.setInput(compressed);
            byte[] uncompressed = new byte[dataLength];
            int written = inflater.inflate(uncompressed);
            if (written != dataLength || !inflater.finished()) {
                throw new DecoderException("zlib inflate mismatch");
            }
            out.add(Unpooled.wrappedBuffer(uncompressed));
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            inflater.end();
        }
    }

    /**
     * @deprecated Pipeline stacking with a frame encoder corrupts frames — use
     *     {@link McOutboundPacketEncoder}.
     */
    @Deprecated
    public static final class Encoder extends MessageToMessageEncoder<ByteBuf> {
        private volatile int threshold = -1;
        private final Deflater deflater = new Deflater();
        private final byte[] encodeBuf = new byte[8192];

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            int readable = msg.readableBytes();
            if (readable == 0) {
                return;
            }
            int t = threshold < 0 ? Integer.MAX_VALUE : threshold;
            out.add(wrapOutbound(ctx.alloc(), msg.retain(), t, deflater, encodeBuf));
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            deflater.end();
        }
    }
}
