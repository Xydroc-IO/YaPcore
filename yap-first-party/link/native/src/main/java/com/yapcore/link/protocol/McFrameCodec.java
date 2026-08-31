package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.zip.Deflater;

/**
 * Length-prefixed Minecraft packet framing.
 * Outbound also applies optional Minecraft zlib compression (same as ViaProxyPipeline.writeFramed)
 * — do not stack a separate MessageToMessageEncoder before this; that path emits empty/corrupt frames.
 */
public final class McFrameCodec {

    private McFrameCodec() {
    }

    public static final class Decoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            in.markReaderIndex();
            if (!readableVarInt(in)) {
                in.resetReaderIndex();
                return;
            }
            int len = McCodec.readVarInt(in);
            if (len <= 0 || len > 2_097_152) {
                if (len == 0) {
                    return; // Skip empty frames (client CorruptedFrameException)
                }
                throw new IllegalArgumentException("Bad packet length " + len);
            }
            if (in.readableBytes() < len) {
                in.resetReaderIndex();
                return;
            }
            out.add(in.readRetainedSlice(len));
        }

        private static boolean readableVarInt(ByteBuf in) {
            if (in.readableBytes() == 0) {
                return false;
            }
            int idx = in.readerIndex();
            for (int i = 0; i < 5; i++) {
                if (in.readableBytes() <= i) {
                    return false;
                }
                if ((in.getByte(idx + i) & 0x80) == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Outbound: optional zlib wrap + length frame in one handler (never emits VarInt(0) frames).
     */
    public static final class Encoder extends ChannelOutboundHandlerAdapter {
        private volatile int compressionThreshold = -1;
        private final Deflater deflater = new Deflater();
        private final byte[] encodeBuf = new byte[8192];

        public void setCompressionThreshold(int threshold) {
            this.compressionThreshold = threshold;
        }

        public int compressionThreshold() {
            return compressionThreshold;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (!(msg instanceof ByteBuf packet)) {
                ctx.write(msg, promise);
                return;
            }
            if (!packet.isReadable()) {
                packet.release();
                promise.setSuccess();
                return;
            }
            ByteBuf payload = null;
            try {
                if (compressionThreshold >= 0) {
                    payload = zlibWrap(ctx, packet, compressionThreshold); // consumes packet
                } else {
                    payload = packet;
                }
                int body = payload.readableBytes();
                if (body <= 0) {
                    payload.release();
                    promise.setSuccess();
                    return;
                }
                ByteBuf framed = ctx.alloc().buffer(body + 5);
                McCodec.writeVarInt(framed, body);
                framed.writeBytes(payload, payload.readerIndex(), body);
                payload.release();
                payload = null;
                ctx.write(framed, promise);
            } catch (Throwable t) {
                ReferenceCountUtil.release(payload);
                promise.setFailure(t instanceof EncoderException ? t : new EncoderException(t));
            }
        }

        private ByteBuf zlibWrap(ChannelHandlerContext ctx, ByteBuf packet, int threshold) {
            int readable = packet.readableBytes();
            if (readable < threshold) {
                ByteBuf out = ctx.alloc().buffer(readable + 5);
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
            ByteBuf out = ctx.alloc().buffer(readable + 5);
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

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            deflater.end();
        }
    }
}
