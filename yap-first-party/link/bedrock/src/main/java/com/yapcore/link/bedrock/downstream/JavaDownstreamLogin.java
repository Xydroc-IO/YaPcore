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

    // Login clientbound (proto 776)
    static final int L_DISCONNECT = 0x00;
    static final int L_SUCCESS = 0x02;
    static final int L_COMPRESSION = 0x03;
    static final int L_CUSTOM_QUERY = 0x04;

    // Login serverbound
    static final int LS_START = 0x00;
    static final int LS_CUSTOM_QUERY_ANSWER = 0x02;
    static final int LS_ACKNOWLEDGED = 0x03;

    // Configuration clientbound (Paper / Folia 26.2 proto 776)
    static final int C_COOKIE_REQUEST = 0x00;
    static final int C_DISCONNECT = 0x02;
    static final int C_FINISH = 0x03;
    static final int C_KEEP_ALIVE = 0x04;
    static final int C_PING = 0x05;
    static final int C_RESOURCE_PACK_POP = 0x08;
    static final int C_RESOURCE_PACK_PUSH = 0x09;
    static final int C_REGISTRY_DATA = 0x07;
    static final int C_SELECT_KNOWN_PACKS = 0x0e;
    static final int C_CODE_OF_CONDUCT = 0x13;

    /**
     * Geyser {@code JavaSelectKnownPacksTranslator} — echo vanilla packs so Folia can omit
     * registry NBT. Empty response forces full NBT (dimension_type doubles, …) over the wire.
     */
    static final java.util.Set<String> KNOWN_PACK_IDS = java.util.Set.of(
            "core", "trade_rebalance", "redstone_experiments", "minecart_improvements");

    // Configuration serverbound
    static final int CS_CLIENT_INFORMATION = 0x00;
    static final int CS_COOKIE_RESPONSE = 0x01;
    static final int CS_FINISH = 0x03;
    static final int CS_KEEP_ALIVE = 0x04;
    static final int CS_PONG = 0x05;
    static final int CS_RESOURCE_PACK = 0x06;
    static final int CS_SELECT_KNOWN_PACKS = 0x07;
    static final int CS_ACCEPT_CODE_OF_CONDUCT = 0x09;

    /** Resource-pack status: SUCCESSFULLY_LOADED / ACCEPTED (Paper waits for both). */
    static final int RP_SUCCESSFULLY_LOADED = 0;
    static final int RP_ACCEPTED = 3;

    private final JavaDownstreamClient client;

    JavaDownstreamLogin(JavaDownstreamClient client) {
        this.client = client;
    }

    void handleLogin(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        switch (packetId) {
            case L_DISCONNECT -> {
                String reason = JavaDownstreamParse.safeString(buf);
                buf.release();
                LOG.warning("JE Login Disconnect: " + reason);
                client.fail(reason);
            }
            case L_COMPRESSION -> {
                int threshold = McCodec.readVarInt(buf);
                buf.release();
                enableCompression(threshold);
                LOG.info("JE Set Compression threshold=" + threshold);
            }
            case L_CUSTOM_QUERY -> {
                int messageId = McCodec.readVarInt(buf);
                String channelName = McCodec.readString(buf, 32767);
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                buf.release();
                handleLoginPluginRequest(ctx, messageId, channelName);
            }
            case L_SUCCESS -> {
                UUID id = McCodec.readUuid(buf);
                String name = McCodec.readString(buf, 16);
                buf.release();
                LOG.info("JE Login Success user=" + name + " uuid=" + id
                        + " (Floodgate identity used for handshake)");
                if (client.listener != null) {
                    client.listener.onLoginSuccess(id, name);
                }
                ByteBuf ack = Unpooled.buffer();
                McCodec.writeVarInt(ack, LS_ACKNOWLEDGED);
                ctx.writeAndFlush(ack);
                client.phase = JavaDownstreamClient.Phase.CONFIGURATION;
                sendClientInformation(ctx, CS_CLIENT_INFORMATION);
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
        McCodec.writeVarInt(resp, LS_CUSTOM_QUERY_ANSWER);
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
            case C_DISCONNECT -> {
                String reason = JavaDownstreamParse.safeString(buf);
                buf.release();
                client.fail(reason);
            }
            case C_COOKIE_REQUEST -> {
                // Folia may ask for cookies; empty payload keeps config moving.
                String key = McCodec.readString(buf, 32767);
                buf.release();
                ByteBuf resp = Unpooled.buffer();
                McCodec.writeVarInt(resp, CS_COOKIE_RESPONSE);
                McCodec.writeString(resp, key);
                resp.writeBoolean(false);
                ctx.writeAndFlush(resp);
                LOG.info("JE Cookie Request → empty response key=" + key);
            }
            case C_KEEP_ALIVE -> {
                long id = buf.readLong();
                buf.release();
                ByteBuf pong = Unpooled.buffer();
                McCodec.writeVarInt(pong, CS_KEEP_ALIVE);
                pong.writeLong(id);
                ctx.writeAndFlush(pong);
            }
            case C_PING -> {
                int id = buf.readInt();
                buf.release();
                ByteBuf pong = Unpooled.buffer();
                McCodec.writeVarInt(pong, CS_PONG);
                pong.writeInt(id);
                ctx.writeAndFlush(pong);
            }
            case C_REGISTRY_DATA -> {
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
            case C_RESOURCE_PACK_POP -> {
                buf.release();
                LOG.fine("JE Resource Pack Pop (ignored)");
            }
            case C_RESOURCE_PACK_PUSH -> {
                // server.properties / YaPPacks push a pack in CONFIG. Paper waits for
                // status before Finish Configuration — without ACK, Bedrock hangs on
                // "locating server" / "generating world" with no StartGame.
                byte[] uuid = new byte[16];
                if (buf.readableBytes() >= 16) {
                    buf.readBytes(uuid);
                }
                buf.release();
                ctx.writeAndFlush(resourcePackStatus(uuid, RP_ACCEPTED));
                ctx.writeAndFlush(resourcePackStatus(uuid, RP_SUCCESSFULLY_LOADED));
                LOG.info("JE Resource Pack Push → auto-accepted (accepted+loaded)");
            }
            case C_SELECT_KNOWN_PACKS -> {
                // Port of Geyser JavaSelectKnownPacksTranslator: accept minecraft known packs
                // with the server's version so Folia omits bulky registry NBT (avoids NBT skip bugs
                // and Bad string length disconnects during configuration).
                int count = McCodec.readVarInt(buf);
                java.util.List<String[]> accepted = new java.util.ArrayList<>();
                for (int i = 0; i < count && buf.isReadable(); i++) {
                    String namespace = McCodec.readString(buf, 32767);
                    String id = McCodec.readString(buf, 32767);
                    String version = McCodec.readString(buf, 32767);
                    if ("minecraft".equals(namespace) && KNOWN_PACK_IDS.contains(id)) {
                        accepted.add(new String[]{namespace, id, version});
                    }
                }
                buf.release();
                ByteBuf packs = Unpooled.buffer();
                McCodec.writeVarInt(packs, CS_SELECT_KNOWN_PACKS);
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
            case C_CODE_OF_CONDUCT -> {
                buf.release();
                ByteBuf accept = Unpooled.buffer(2);
                McCodec.writeVarInt(accept, CS_ACCEPT_CODE_OF_CONDUCT);
                ctx.writeAndFlush(accept);
                LOG.info("JE Code of Conduct → accepted");
            }
            case C_FINISH -> {
                buf.release();
                ByteBuf finish = Unpooled.buffer();
                McCodec.writeVarInt(finish, CS_FINISH);
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
        McCodec.writeVarInt(out, CS_RESOURCE_PACK);
        out.writeBytes(uuid != null && uuid.length == 16 ? uuid : new byte[16]);
        McCodec.writeVarInt(out, result);
        return out;
    }

    void sendClientInformation(ChannelHandlerContext ctx, int packetId) {
        // Join-capped view until Bedrock 0x71 (see JavaLoginTranslator.JOIN_BEDROCK_VIEW).
        // Full JE disk is requested only after SetLocalPlayerAsInitialized — requesting 32
        // here flooded ~3500 LevelChunks and left clients on "loading" for minutes.
        int view = client.requestedViewDistance > 0
                ? client.requestedViewDistance
                : JavaDownstreamClient.DEFAULT_VIEW_DISTANCE;
        ctx.writeAndFlush(JavaPlayWire.clientInformation(packetId, view));
        LOG.info("JE Client Information view=" + Math.max(2, Math.min(32, view))
                + " phase=" + client.phase + " user=" + client.username);
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
        McCodec.writeVarInt(login, LS_START);
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
