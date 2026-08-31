package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/** Length-prefixed Minecraft packet framing (inbound). Outbound: {@link McOutboundPacketEncoder}. */
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
                    return; // skip empty frames — vanilla CorruptedFrameException
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
}
