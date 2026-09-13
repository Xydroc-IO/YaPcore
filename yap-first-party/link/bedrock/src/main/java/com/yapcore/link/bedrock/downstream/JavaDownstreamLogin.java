package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import com.yapcore.protocol.McCompressionCodec;
import com.yapcore.protocol.McOutboundPacketEncoder;
import com.yapcore.protocol.forwarding.ModernForwarding;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Login + configuration phase handlers (split from {@link JavaDownstreamClient}). */
final class JavaDownstreamLogin {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final JavaDownstreamClient client;

    JavaDownstreamLogin(JavaDownstreamClient client) {
        this.client = client;
    }

    void handleLogin(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        switch (packetId) {
            case JavaDownstreamClient.L_DISCONNECT -> {
                String reason = JavaDownstreamParse.safeString(buf);
                buf.release();
                LOG.warning("JE Login Disconnect: " + reason);
                client.fail(reason);
            }
            case JavaDownstreamClient.L_COMPRESSION -> {
                int threshold = McCodec.readVarInt(buf);
                buf.release();
                enableCompression(threshold);
                LOG.info("JE Set Compression threshold=" + threshold);
            }
            case JavaDownstreamClient.L_CUSTOM_QUERY -> {
                int messageId = McCodec.readVarInt(buf);
                String channelName = McCodec.readString(buf, 32767);
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                buf.release();
                handleLoginPluginRequest(ctx, messageId, channelName);
            }
            case JavaDownstreamClient.L_SUCCESS -> {
                UUID id = McCodec.readUuid(buf);
                String name = McCodec.readString(buf, 16);
                buf.release();
                LOG.info("JE Login Success user=" + name + " uuid=" + id
                        + " (Floodgate identity used for handshake)");
                if (client.listener != null) {
                    client.listener.onLoginSuccess(id, name);
                }
                ByteBuf ack = Unpooled.buffer();
                McCodec.writeVarInt(ack, JavaDownstreamClient.LS_ACKNOWLEDGED);
                ctx.writeAndFlush(ack);
                client.phase = JavaDownstreamClient.Phase.CONFIGURATION;
                sendClientInformation(ctx, JavaDownstreamClient.CS_CLIENT_INFORMATION);
                LOG.info("JE → Configuration (login acknowledged)");
            }
            default -> {
                LOG.fine("JE login skip id=0x" + Integer.toHexString(packetId));
                buf.release();
            }
        }
    }

    void handleLoginPluginRequest(ChannelHandlerContext ctx, int messageId, String channelName) {
        ByteBuf resp = Unpooled.buffer();
        McCodec.writeVarInt(resp, JavaDownstreamClient.LS_CUSTOM_QUERY_ANSWER);
        McCodec.writeVarInt(resp, messageId);
        if (ModernForwarding.CHANNEL.equals(channelName)
                && client.forwardingSecret != null && client.forwardingSecret.length > 0) {
            ByteBuf payload = ModernForwarding.createForwardingData(
                    client.forwardingSecret,
                    client.clientAddress,
                    client.playerId,
                    client.username,
                    java.util.List.of());
            resp.writeBoolean(true);
            resp.writeBytes(payload);
            payload.release();
            ctx.writeAndFlush(resp);
            LOG.info("JE Velocity modern forwarding injected channel=" + channelName);
        } else {
            resp.writeBoolean(false);
            ctx.writeAndFlush(resp);
            LOG.info("JE Login Plugin Request channel=" + channelName + " (no payload)");
        }
    }

    void handleConfiguration(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        switch (packetId) {
            case JavaDownstreamClient.C_DISCONNECT -> {
                String reason = JavaDownstreamParse.safeString(buf);
                buf.release();
                client.fail(reason);
            }
            case JavaDownstreamClient.C_COOKIE_REQUEST -> {
                // Folia may ask for cookies; empty payload keeps config moving.
                String key = McCodec.readString(buf, 32767);
                buf.release();
                ByteBuf resp = Unpooled.buffer();
                McCodec.writeVarInt(resp, JavaDownstreamClient.CS_COOKIE_RESPONSE);
                McCodec.writeString(resp, key);
                resp.writeBoolean(false);
                ctx.writeAndFlush(resp);
                LOG.info("JE Cookie Request → empty response key=" + key);
            }
            case JavaDownstreamClient.C_KEEP_ALIVE -> {
                long id = buf.readLong();
                buf.release();
                ByteBuf pong = Unpooled.buffer();
                McCodec.writeVarInt(pong, JavaDownstreamClient.CS_KEEP_ALIVE);
                pong.writeLong(id);
                ctx.writeAndFlush(pong);
            }
            case JavaDownstreamClient.C_PING -> {
                int id = buf.readInt();
                buf.release();
                ByteBuf pong = Unpooled.buffer();
                McCodec.writeVarInt(pong, JavaDownstreamClient.CS_PONG);
                pong.writeInt(id);
                ctx.writeAndFlush(pong);
            }
            case JavaDownstreamClient.C_REGISTRY_DATA -> {
                // Defensive: malformed NBT must not kill the JE downstream (probe: Bad string length).
                try {
                    String registryId = McCodec.readString(buf, 32767);
                    int count = McCodec.readVarInt(buf);
                    if ("minecraft:block".equals(registryId)) {
                        // TYPE registry (~1k ids) is NOT global block-state ids (~32k).
                        // Chunk palettes use state ids from protocol/java/26_2/block_states.txt.
                        // Do not feed type names into JeBlockRegistry as state overrides.
                        for (int i = 0; i < count && buf.isReadable(); i++) {
                            McCodec.readString(buf, 32767);
                            if (buf.readBoolean()) {
                                JavaDownstreamNbt.skipNbtPayload(buf);
                            }
                        }
                        LOG.info("JE registry_data minecraft:block types=" + count
                                + " (ignored for state map; staticStates="
                                + JeBlockRegistry.staticSize() + ") user=" + client.username);
                    } else {
                        for (int i = 0; i < count && buf.isReadable(); i++) {
                            McCodec.readString(buf, 32767);
                            if (buf.readBoolean()) {
                                JavaDownstreamNbt.skipNbtPayload(buf);
                            }
                        }
                        LOG.fine("JE registry_data " + registryId + " entries=" + count
                                + " user=" + client.username);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "JE registry_data skip remainder user=" + client.username
                            + ": " + e.getMessage(), e);
                    if (buf.isReadable()) {
                        buf.skipBytes(buf.readableBytes());
                    }
                }
                buf.release();
            }
            case JavaDownstreamClient.C_RESOURCE_PACK_POP -> {
                buf.release();
                LOG.fine("JE Resource Pack Pop (ignored)");
            }
            case JavaDownstreamClient.C_RESOURCE_PACK_PUSH -> {
                // server.properties / YaPPacks push a pack in CONFIG. Paper waits for
                // status before Finish Configuration — without ACK, Bedrock hangs on
                // "locating server" / "generating world" with no StartGame.
                byte[] uuid = new byte[16];
                if (buf.readableBytes() >= 16) {
                    buf.readBytes(uuid);
                }
                buf.release();
                ctx.writeAndFlush(resourcePackStatus(uuid, JavaDownstreamClient.RP_ACCEPTED));
                ctx.writeAndFlush(resourcePackStatus(uuid, JavaDownstreamClient.RP_SUCCESSFULLY_LOADED));
                LOG.info("JE Resource Pack Push → auto-accepted (accepted+loaded)");
            }
            case JavaDownstreamClient.C_SELECT_KNOWN_PACKS -> {
                // Port of Geyser JavaSelectKnownPacksTranslator: accept minecraft known packs
                // with the server's version so Folia omits bulky registry NBT (avoids NBT skip bugs
                // and Bad string length disconnects during configuration).
                int count = McCodec.readVarInt(buf);
                java.util.List<String[]> accepted = new java.util.ArrayList<>();
                for (int i = 0; i < count && buf.isReadable(); i++) {
                    String namespace = McCodec.readString(buf, 32767);
                    String id = McCodec.readString(buf, 32767);
                    String version = McCodec.readString(buf, 32767);
                    if ("minecraft".equals(namespace) && JavaDownstreamClient.KNOWN_PACK_IDS.contains(id)) {
                        accepted.add(new String[]{namespace, id, version});
                    }
                }
                buf.release();
                ByteBuf packs = Unpooled.buffer();
                McCodec.writeVarInt(packs, JavaDownstreamClient.CS_SELECT_KNOWN_PACKS);
                McCodec.writeVarInt(packs, accepted.size());
                for (String[] pack : accepted) {
                    McCodec.writeString(packs, pack[0]);
                    McCodec.writeString(packs, pack[1]);
                    McCodec.writeString(packs, pack[2]);
                }
                ctx.writeAndFlush(packs);
                LOG.info("JE Select Known Packs → accepted=" + accepted.size()
                        + "/" + count + " (Geyser echo)");
            }
            case JavaDownstreamClient.C_CODE_OF_CONDUCT -> {
                buf.release();
                ByteBuf accept = Unpooled.buffer(2);
                McCodec.writeVarInt(accept, JavaDownstreamClient.CS_ACCEPT_CODE_OF_CONDUCT);
                ctx.writeAndFlush(accept);
                LOG.info("JE Code of Conduct → accepted");
            }
            case JavaDownstreamClient.C_FINISH -> {
                buf.release();
                ByteBuf finish = Unpooled.buffer();
                McCodec.writeVarInt(finish, JavaDownstreamClient.CS_FINISH);
                ctx.writeAndFlush(finish);
                client.phase = JavaDownstreamClient.Phase.PLAY;
                LOG.info("JE Configuration complete → Play");
                if (client.listener != null) {
                    client.listener.onConfigurationComplete();
                }
            }
            default -> {
                LOG.info("JE config accept id=0x" + Integer.toHexString(packetId)
                        + " len=" + buf.readableBytes());
                buf.release();
            }
        }
    }

    static ByteBuf resourcePackStatus(byte[] uuid, int result) {
        ByteBuf out = Unpooled.buffer(24);
        McCodec.writeVarInt(out, JavaDownstreamClient.CS_RESOURCE_PACK);
        out.writeBytes(uuid != null && uuid.length == 16 ? uuid : new byte[16]);
        McCodec.writeVarInt(out, result);
        return out;
    }

    void sendClientInformation(ChannelHandlerContext ctx, int packetId) {
        ByteBuf info = Unpooled.buffer();
        McCodec.writeVarInt(info, packetId);
        McCodec.writeString(info, "en_US");
        info.writeByte(8);
        McCodec.writeVarInt(info, 0);
        info.writeBoolean(true);
        info.writeByte(0x7f);
        McCodec.writeVarInt(info, 1);
        info.writeBoolean(false);
        info.writeBoolean(true);
        McCodec.writeVarInt(info, 0);
        ctx.writeAndFlush(info);
    }

    void sendHandshake() {
        ByteBuf hs = Unpooled.buffer();
        McCodec.writeVarInt(hs, 0x00);
        McCodec.writeVarInt(hs, client.protocolVersion);
        McCodec.writeString(hs, client.backend.getHostString());
        hs.writeShort(client.backend.getPort());
        McCodec.writeVarInt(hs, 2); // next state = login
        client.channel.writeAndFlush(hs);
    }

    void sendLoginStart() {
        ByteBuf login = Unpooled.buffer();
        McCodec.writeVarInt(login, JavaDownstreamClient.LS_START);
        McCodec.writeString(login, client.username);
        McCodec.writeUuid(login, client.playerId);
        client.channel.writeAndFlush(login);
    }

    void enableCompression(int threshold) {
        if (threshold < 0 || client.channel == null) {
            return;
        }
        if (client.compDec == null) {
            client.compDec = new McCompressionCodec.Decoder();
            client.channel.pipeline().addAfter("frame-dec", "comp-dec", client.compDec);
        }
        client.compDec.setThreshold(threshold);
        Object enc = client.channel.pipeline().get("frame-enc");
        if (enc instanceof McOutboundPacketEncoder outbound) {
            outbound.setCompressionThreshold(threshold);
        }
    }

}
