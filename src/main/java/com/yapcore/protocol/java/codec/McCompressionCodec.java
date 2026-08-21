package com.yapcore.protocol.java.codec;

import io.netty.buffer.ByteBuf;
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
 * Minecraft JE packet compression (zlib) between frame codec and packet handler.
 * Frame body layout when enabled: VarInt(dataLength) + data — dataLength 0 means
 * uncompressed packet follows; otherwise zlib payload expands to dataLength bytes.
 */
public final class McCompressionCodec {

    private static final int MAX_UNCOMPRESSED = 8 * 1024 * 1024;

    private McCompressionCodec() {
    }

    public static final class Decoder extends MessageToMessageDecoder<ByteBuf> {
        private volatile int threshold = -1;
        private final Inflater inflater = new Inflater();

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int threshold() {
            return threshold;
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
                throw new DecoderException("Badly compressed packet: size " + dataLength
                        + " below threshold " + threshold);
            }
            if (dataLength > MAX_UNCOMPRESSED) {
                throw new DecoderException("Badly compressed packet: size " + dataLength
                        + " exceeds " + MAX_UNCOMPRESSED);
            }
            byte[] compressed = new byte[msg.readableBytes()];
            msg.readBytes(compressed);
            inflater.reset();
            inflater.setInput(compressed);
            byte[] uncompressed = new byte[dataLength];
            int written = inflater.inflate(uncompressed);
            if (written != dataLength || !inflater.finished()) {
                throw new DecoderException("zlib inflate mismatch written=" + written
                        + " expected=" + dataLength);
            }
            out.add(Unpooled.wrappedBuffer(uncompressed));
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            inflater.end();
        }
    }

    public static final class Encoder extends MessageToMessageEncoder<ByteBuf> {
        private volatile int threshold = -1;
        private final Deflater deflater = new Deflater();
        private final byte[] encodeBuf = new byte[8192];

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int threshold() {
            return threshold;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            int readable = msg.readableBytes();
            ByteBuf outBuf = ctx.alloc().buffer(readable + 5);
            try {
                if (threshold < 0 || readable < threshold) {
                    McCodec.writeVarInt(outBuf, 0);
                    outBuf.writeBytes(msg, msg.readerIndex(), readable);
                } else {
                    byte[] input = new byte[readable];
                    msg.getBytes(msg.readerIndex(), input);
                    deflater.reset();
                    deflater.setInput(input);
                    deflater.finish();
                    McCodec.writeVarInt(outBuf, readable);
                    while (!deflater.finished()) {
                        int n = deflater.deflate(encodeBuf);
                        if (n <= 0) {
                            break;
                        }
                        outBuf.writeBytes(encodeBuf, 0, n);
                    }
                    if (!deflater.finished()) {
                        throw new EncoderException("zlib deflate did not finish");
                    }
                }
                out.add(outBuf);
            } catch (RuntimeException e) {
                outBuf.release();
                throw e;
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            deflater.end();
        }
    }
}
