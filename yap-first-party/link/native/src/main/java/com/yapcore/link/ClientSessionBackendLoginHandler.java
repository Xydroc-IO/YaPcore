package com.yapcore.link;

import com.yapcore.link.forwarding.ModernForwarding;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McCompressionCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

final class ClientSessionBackendLoginHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    private final ClientSession session;
    private final Channel client;
    private boolean forwarded;
    private McCompressionCodec.Decoder clientCompDec;
    private McCompressionCodec.Encoder clientCompEnc;
    private McCompressionCodec.Decoder backendCompDec;
    private McCompressionCodec.Encoder backendCompEnc;

    ClientSessionBackendLoginHandler(ClientSession session, Channel client) {
        this.session = session;
        this.client = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        buf.markReaderIndex();
        int packetId = McCodec.readVarInt(buf);
        try {
            if (packetId == 0x00) {
                buf.resetReaderIndex();
                client.writeAndFlush(buf.retain()).addListener(ChannelFutureListener.CLOSE);
                ctx.close();
                return;
            }
            if (packetId == 0x03) {
                int threshold = McCodec.readVarInt(buf);
                buf.release();
                enableCompression(client, ctx.channel(), threshold);
                forwardSetCompression(threshold);
                return;
            }
            if (packetId == 0x04) {
                handlePluginRequest(ctx, buf);
                return;
            }
            if (packetId == 0x02) {
                if (!forwarded) {
                    buf.release();
                    session.kickChannel(client, "Backend did not request modern forwarding");
                    ctx.close();
                    return;
                }
                buf.resetReaderIndex();
                client.writeAndFlush(buf.retain());
                session.beginBridge(client, ctx.channel());
                return;
            }
            buf.release();
            session.kickChannel(client, "Unexpected backend login packet 0x" + Integer.toHexString(packetId));
            ctx.close();
        } catch (Exception e) {
            buf.release();
            LOG.log(Level.WARNING, "backend login failed", e);
            session.kickChannel(client, "Backend login error");
            ctx.close();
        }
    }

    private void handlePluginRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int messageId = McCodec.readVarInt(buf);
        String channel = McCodec.readString(buf, 32767);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        if (ModernForwarding.CHANNEL.equals(channel)) {
            ByteBuf payload = ModernForwarding.createForwardingData(
                    session.server.config().forwardingSecret(),
                    session.clientAddress,
                    session.playerId,
                    session.username,
                    session.properties
            );
            ByteBuf resp = Unpooled.buffer();
            McCodec.writeVarInt(resp, 0x02);
            McCodec.writeVarInt(resp, messageId);
            resp.writeBoolean(true);
            resp.writeBytes(payload);
            payload.release();
            ctx.writeAndFlush(resp);
            forwarded = true;
        } else {
            ByteBuf resp = Unpooled.buffer();
            McCodec.writeVarInt(resp, 0x02);
            McCodec.writeVarInt(resp, messageId);
            resp.writeBoolean(false);
            ctx.writeAndFlush(resp);
        }
    }

    private void forwardSetCompression(int threshold) {
        ByteBuf fwd = Unpooled.buffer();
        McCodec.writeVarInt(fwd, 0x03);
        McCodec.writeVarInt(fwd, threshold);
        client.writeAndFlush(fwd);
    }

    private void enableCompression(Channel clientCh, Channel backendCh, int threshold) {
        if (threshold < 0) {
            return;
        }
        if (backendCompDec == null) {
            backendCompDec = new McCompressionCodec.Decoder();
            backendCompEnc = new McCompressionCodec.Encoder();
            backendCh.pipeline().addAfter("frame-dec", "comp-dec", backendCompDec);
            backendCh.pipeline().addAfter("frame-enc", "comp-enc", backendCompEnc);
        }
        backendCompDec.setThreshold(threshold);
        backendCompEnc.setThreshold(threshold);

        if (clientCompDec == null) {
            clientCompDec = new McCompressionCodec.Decoder();
            clientCompEnc = new McCompressionCodec.Encoder();
            clientCh.pipeline().addAfter("frame-dec", "comp-dec", clientCompDec);
            clientCh.pipeline().addBefore("frame-enc", "comp-enc", clientCompEnc);
        }
        clientCompDec.setThreshold(threshold);
        clientCompEnc.setThreshold(threshold);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        client.close();
    }
}
