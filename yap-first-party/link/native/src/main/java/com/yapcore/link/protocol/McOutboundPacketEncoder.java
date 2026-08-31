package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;

import java.util.zip.Deflater;

/**
 * Single outbound handler: optional Minecraft zlib + length frame.
 * Same contract as chassis {@code ViaProxyPipeline.writeFramed} — never install a separate
 * compress MessageToMessageEncoder before this (vanilla {@code Frame length cannot be zero}).
 */
public final class McOutboundPacketEncoder extends ChannelOutboundHandlerAdapter {

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
                payload = McCompressionCodec.wrapOutbound(
                        ctx.alloc(), packet, compressionThreshold, deflater, encodeBuf);
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

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        deflater.end();
    }
}
