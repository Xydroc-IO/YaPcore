package com.yapcore.link;

import com.yapcore.link.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Client-side handler during soft switch (play → config → play). */
final class SoftSwitchClientHandler extends ChannelInboundHandlerAdapter {
    private final ClientSession session;
    private final AtomicBoolean configAcked = new AtomicBoolean(false);
    private final AtomicBoolean finishSent = new AtomicBoolean(false);

    SoftSwitchClientHandler(ClientSession session) {
        this.session = session;
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
        buf.markReaderIndex();
        int packetId;
        try {
            packetId = McCodec.readVarInt(buf);
        } catch (Exception e) {
            buf.release();
            return;
        }

        // Waiting for play configuration_acknowledged after start_configuration.
        if (!configAcked.get()) {
            if (packetId == ClientSessionSoftSwitch.PLAY_SB_CONFIGURATION_ACK) {
                buf.release();
                if (configAcked.compareAndSet(false, true)) {
                    ClientSessionSoftSwitch.LOG.info("SOFT-SWITCH config-ack user=" + session.username);
                    session.clientInConfig = true;
                    Channel backend = session.backend;
                    if (backend != null && backend.isActive()) {
                        // Resume config forwarding from backend.
                        Object h = backend.pipeline().get("backend");
                        if (h instanceof SoftSwitchBackendLoginHandler switchBackend) {
                            switchBackend.onClientConfigReady();
                        }
                    }
                }
                return;
            }
            // Drop play packets while waiting for config ack.
            buf.release();
            return;
        }

        // In configuration: forward to backend; watch finish_configuration.
        if (packetId == ClientSessionSoftSwitch.CONFIG_FINISH) {
            if (!finishSent.compareAndSet(false, true)) {
                buf.release();
                return;
            }
            ClientSessionSoftSwitch.LOG.info("SOFT-SWITCH finish-ack user=" + session.username
                    + " → play bridge on " + session.currentBackendName);
            session.clientInConfig = false;
            session.switching.set(false);
            session.pendingSwitchTarget = null;
            Channel backend = session.backend;
            // Install play relays BEFORE acking the backend so Join Game hits them.
            session.rebridgeAfterSwitch(ctx.channel(), backend);
            buf.resetReaderIndex();
            if (backend != null && backend.isActive()) {
                backend.writeAndFlush(buf);
            } else {
                buf.release();
            }
            return;
        }

        buf.resetReaderIndex();
        Channel backend = session.backend;
        if (backend != null && backend.isActive()) {
            backend.writeAndFlush(buf);
        } else {
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        session.switching.set(false);
        Channel backend = session.backend;
        if (backend != null) {
            backend.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ClientSessionSoftSwitch.LOG.log(Level.WARNING, "soft-switch client error user=" + session.username, cause);
        session.switching.set(false);
        ctx.close();
        if (session.backend != null) {
            session.backend.close();
        }
    }
}
