package com.yapcore.link.bedrock.downstream;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.logging.Level;

/** Netty inbound for one Java downstream (split from {@link JavaDownstreamClient}). */
final class JavaDownstreamInbound extends ChannelInboundHandlerAdapter {

    private final JavaDownstreamClient client;

    JavaDownstreamInbound(JavaDownstreamClient client) {
        this.client = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        try {
            switch (client.phase) {
                case LOGIN -> client.login.handleLogin(ctx, buf);
                case CONFIGURATION -> client.login.handleConfiguration(ctx, buf);
                case PLAY -> client.play.handlePlay(ctx, buf);
                default -> buf.release();
            }
        } catch (Exception e) {
            // handlePlay already releases in its finally — never double-release
            // (that masked real parse errors as "refCnt: 0, decrement: 1" / IC-41).
            if (buf.refCnt() > 0) {
                buf.release();
            }
            JavaDownstreamClient.LOG.log(Level.WARNING, "JE downstream packet error phase=" + client.phase, e);
            client.fail("Java downstream error: " + e.getMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!client.closed.get()) {
            client.fail("Java backend closed");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        JavaDownstreamClient.LOG.log(Level.FINE, "JE downstream exception", cause);
        client.fail(cause.getMessage() != null ? cause.getMessage() : "exception");
    }
}
