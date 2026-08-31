package com.yapcore.link;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

final class ClientSessionPlayRelay extends ChannelInboundHandlerAdapter {

    private final ClientSession session;
    private final Channel peer;
    private final boolean fromClient;

    ClientSessionPlayRelay(ClientSession session, Channel peer, boolean fromClient) {
        this.session = session;
        this.peer = peer;
        this.fromClient = fromClient;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        if (!buf.isReadable()) {
            buf.release();
            return;
        }
        if (fromClient) {
            if (session.server.config().enableServerCommand()) {
                String cmd = ClientSessionRouting.extractServerCommand(buf);
                if (cmd != null) {
                    buf.release();
                    ClientSessionRouting.handleServerCommand(session, cmd);
                    return;
                }
            }
            session.server.chatRelay().tryRelayClientPacket(
                    session.protocolVersion, session.playerId, session.username, session.currentBackendName, buf);
            ClientSessionRouting.tryFirePluginMessage(session, buf, true);
        } else {
            ClientSessionRouting.tryFirePluginMessage(session, buf, false);
        }
        if (peer.isActive()) {
            peer.writeAndFlush(msg);
        } else {
            buf.release();
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        peer.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        peer.close();
    }
}
