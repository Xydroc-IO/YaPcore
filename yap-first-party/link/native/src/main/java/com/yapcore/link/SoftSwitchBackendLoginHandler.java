package com.yapcore.link;

import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McOutboundPacketEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;

/**
 * Backend login during soft switch: suppress Login Success to client, ACK login,
 * then enter config passthrough after client acknowledges start_configuration.
 */
final class SoftSwitchBackendLoginHandler extends ChannelInboundHandlerAdapter {
    private final ClientSession session;
    private final Channel client;
    private boolean forwarded;
    private boolean loginSuccessSeen;
    private boolean clientConfigReady;
    private final java.util.ArrayList<ByteBuf> pendingConfig = new java.util.ArrayList<>();
    private com.yapcore.link.protocol.McCompressionCodec.Decoder backendCompDec;
    private ChannelHandlerContext ctxRef;

    SoftSwitchBackendLoginHandler(ClientSession session, Channel client) {
        this.session = session;
        this.client = client;
    }

    void onClientConfigReady() {
        clientConfigReady = true;
        Channel backend = ctxRef != null ? ctxRef.channel() : session.backend;
        if (backend != null && backend.isActive() && loginSuccessSeen) {
            ClientSessionSoftSwitch.sendLoginAcknowledged(backend);
            backend.config().setAutoRead(true);
        }
        for (ByteBuf pending : pendingConfig) {
            if (client.isActive()) {
                client.writeAndFlush(pending);
            } else {
                pending.release();
            }
        }
        pendingConfig.clear();
        if (loginSuccessSeen && ctxRef != null) {
            enterConfigRelay(ctxRef);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctxRef = ctx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        buf.markReaderIndex();
        int packetId = McCodec.readVarInt(buf);
        try {
            if (!loginSuccessSeen) {
                handleLoginPhase(ctx, buf, packetId);
                return;
            }
            // Configuration packets from backend.
            buf.resetReaderIndex();
            if (!clientConfigReady) {
                pendingConfig.add(buf.retain());
                buf.release();
                return;
            }
            if (packetId == ClientSessionSoftSwitch.CONFIG_FINISH) {
                // Forward finish_configuration; client ack triggers rebridge.
                buf.resetReaderIndex();
                client.writeAndFlush(buf);
                return;
            }
            buf.resetReaderIndex();
            client.writeAndFlush(buf);
        } catch (Exception e) {
            buf.release();
            ClientSessionSoftSwitch.LOG.log(Level.WARNING, "soft-switch backend login failed", e);
            session.switching.set(false);
            session.kickChannel(client, "Backend switch error");
            ctx.close();
        }
    }

    private void handleLoginPhase(ChannelHandlerContext ctx, ByteBuf buf, int packetId) throws Exception {
        if (packetId == 0x00) {
            // Login disconnect — never forward raw login packets to a play client
            // (id 0 looks like play bundle_delimiter → DecoderException).
            String reason = "Backend rejected switch";
            try {
                reason = McCodec.readString(buf, 32767);
            } catch (Exception ignored) {
                // keep default
            }
            buf.release();
            session.switching.set(false);
            session.kickChannel(client, reason);
            ctx.close();
            return;
        }
        if (packetId == 0x03) {
            int threshold = McCodec.readVarInt(buf);
            buf.release();
            // Client already has compression from first join — only update backend codec.
            enableBackendCompression(ctx.channel(), threshold);
            // Keep client threshold in sync if backend differs.
            Object clientEnc = client.pipeline().get("frame-enc");
            if (clientEnc instanceof McOutboundPacketEncoder enc) {
                enc.setCompressionThreshold(threshold);
            }
            Object clientDec = client.pipeline().get("comp-dec");
            if (clientDec instanceof com.yapcore.link.protocol.McCompressionCodec.Decoder dec) {
                dec.setThreshold(threshold);
            } else if (threshold >= 0) {
                var dec = new com.yapcore.link.protocol.McCompressionCodec.Decoder();
                client.pipeline().addAfter("frame-dec", "comp-dec", dec);
                dec.setThreshold(threshold);
            }
            return;
        }
        if (packetId == 0x04) {
            handlePluginRequest(ctx, buf);
            return;
        }
        if (packetId == 0x05) {
            // Login cookie_request (1.20.5+) — reply empty; never forward to play client.
            handleLoginCookieRequest(ctx, buf);
            return;
        }
        if (packetId == 0x02) {
            buf.release(); // do NOT send Login Success to an already-playing client
            loginSuccessSeen = true;
            if (!forwarded) {
                ClientSessionSoftSwitch.LOG.info("SOFT-SWITCH login-success (no modern fwd) user=" + session.username);
            } else {
                ClientSessionSoftSwitch.LOG.info("SOFT-SWITCH login-success user=" + session.username);
            }
            // Pause backend until the client enters configuration (Velocity order).
            ctx.channel().config().setAutoRead(false);
            ClientSessionSoftSwitch.sendStartConfiguration(client);
            // Login ACK after client config-ack (onClientConfigReady).
            return;
        }
        buf.release();
        session.switching.set(false);
        ClientSessionSoftSwitch.LOG.warning("SOFT-SWITCH unexpected login packet 0x" + Integer.toHexString(packetId)
                + " user=" + session.username);
        session.kickChannel(client, "Unexpected backend packet during switch");
        ctx.close();
    }

    private void handleLoginCookieRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        String key = McCodec.readString(buf, 32767);
        buf.release();
        ByteBuf resp = Unpooled.buffer();
        McCodec.writeVarInt(resp, 0x04); // login cookie_response
        McCodec.writeString(resp, key);
        resp.writeBoolean(false);
        ctx.writeAndFlush(resp);
        ClientSessionSoftSwitch.LOG.fine("SOFT-SWITCH cookie_request key=" + key + " user=" + session.username);
    }

    private void enterConfigRelay(ChannelHandlerContext ctx) {
        // Stay as this handler — channelRead already relays config once loginSuccessSeen.
        session.phase = ClientSession.Phase.BRIDGING;
    }

    private void handlePluginRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int messageId = McCodec.readVarInt(buf);
        String channel = McCodec.readString(buf, 32767);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        if (com.yapcore.link.forwarding.ModernForwarding.CHANNEL.equals(channel)) {
            ByteBuf payload = com.yapcore.link.forwarding.ModernForwarding.createForwardingData(
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

    private void enableBackendCompression(Channel backendCh, int threshold) {
        if (threshold < 0) {
            return;
        }
        if (backendCompDec == null) {
            backendCompDec = new com.yapcore.link.protocol.McCompressionCodec.Decoder();
            backendCh.pipeline().addAfter("frame-dec", "comp-dec", backendCompDec);
        }
        backendCompDec.setThreshold(threshold);
        Object backendEnc = backendCh.pipeline().get("frame-enc");
        if (backendEnc instanceof McOutboundPacketEncoder enc) {
            enc.setCompressionThreshold(threshold);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session.switching.get()) {
            ClientSessionSoftSwitch.LOG.warning("SOFT-SWITCH backend dropped during switch user=" + session.username);
            session.switching.set(false);
            if (client.isActive()) {
                session.kickChannel(client, "Lost connection while switching servers");
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ClientSessionSoftSwitch.LOG.log(Level.WARNING, "soft-switch backend error", cause);
        session.switching.set(false);
        ctx.close();
        if (client.isActive()) {
            session.kickChannel(client, "Backend switch error");
        }
    }
}
