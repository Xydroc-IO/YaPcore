package com.yapcore.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/** Length-prefixed Minecraft packet framing (uncompressed). */
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
            if (len < 0 || len > 2_097_152) {
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

    public static final class Encoder extends MessageToByteEncoder<ByteBuf> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
            int body = msg.readableBytes();
            McCodec.writeVarInt(out, body);
            out.writeBytes(msg, msg.readerIndex(), body);
        }
    }
}
