package com.yapcore.protocol.via.transform;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.forward.ForwardTransformer;
import com.yapcore.protocol.via.id.PacketIdTable;
import com.yapcore.protocol.via.mid.MidBandTransformer;
import com.yapcore.protocol.via.remap.PlayPacketRemapper;
import com.yapcore.protocol.via.rewind.Rewind18Transformer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.logging.Logger;

/**
 * Translates framed Minecraft packets between client and server protocol bands.
 * Phase 4: Forward (4.V1) + Mid dump-backed (4.V2 modern) + Rewind (4.V3) + Play remapper.
 */
public final class PacketTransformer {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaXform");

    private final PacketIdTable clientTable;
    private final PacketIdTable serverTable;
    private final PlayPacketRemapper playRemapper;
    private final Rewind18Transformer rewind;
    private final ForwardTransformer forward;
    private final MidBandTransformer mid;

    public PacketTransformer(ViaSession session) {
        this.clientTable = PacketIdTable.forBand(session.clientBand());
        this.serverTable = PacketIdTable.forBand(session.serverBand());
        this.playRemapper = new PlayPacketRemapper(session);
        this.rewind = Rewind18Transformer.applies(session) ? new Rewind18Transformer(session) : null;
        this.forward = ForwardTransformer.applies(session) ? new ForwardTransformer(session) : null;
        this.mid = MidBandTransformer.applies(session) ? new MidBandTransformer(session) : null;
    }

    public ByteBuf transform(ViaSession session, ViaDirection direction, ByteBuf packet) {
        if (!session.needsRemap()) {
            return packet.retainedDuplicate();
        }
        if (!packet.isReadable()) {
            return null;
        }
        int reader = packet.readerIndex();
        int packetId;
        try {
            packetId = McCodec.readVarInt(packet);
        } catch (Exception e) {
            packet.readerIndex(reader);
            return packet.retainedDuplicate();
        }
        ConnState state = session.state();
        return switch (direction) {
            case CLIENTBOUND_TO_SERVER -> transformC2S(session, state, packetId, packet);
            case SERVERBOUND_TO_CLIENT -> transformS2C(session, state, packetId, packet);
        };
    }

    private ByteBuf transformC2S(ViaSession session, ConnState state, int clientId, ByteBuf body) {
        if (state == ConnState.PLAY) {
            if (forward != null) {
                ByteBuf f = forward.transform(session, ViaDirection.CLIENTBOUND_TO_SERVER, clientId, body);
                if (f != null) {
                    return f;
                }
            }
            if (mid != null) {
                ByteBuf m = mid.transform(session, ViaDirection.CLIENTBOUND_TO_SERVER, clientId, body);
                if (m != null) {
                    return m;
                }
                // Mid owns modern paths: unknown → drop (no same-ID leak)
                if (mid.clientDump().hasPlay()) {
                    return null;
                }
            }
            if (rewind != null) {
                ByteBuf r = rewind.transform(session, ViaDirection.CLIENTBOUND_TO_SERVER, clientId, body);
                if (r != null) {
                    return r;
                }
            }
            ByteBuf play = playRemapper.remap(session, ViaDirection.CLIENTBOUND_TO_SERVER, clientId, body);
            if (play != null) {
                return play;
            }
        }
        PacketIdTable.Packet kind = clientTable.c2sPacket(state, clientId);
        if (kind == null) {
            if ((state == ConnState.CONFIG || state == ConnState.LOGIN) && clientId == 0x00
                    && body.isReadable()) {
                ByteBuf modern = rewriteClientInformation(body);
                if (modern != null) {
                    return modern;
                }
            }
            if (state == ConnState.CONFIG && ConfigPacketRemapper.needsRemap(session)) {
                ByteBuf cfg = ConfigPacketRemapper.remapC2S(session, clientId, body);
                if (cfg != null) {
                    return cfg;
                }
                return null;
            }
            return rewriteId(body, clientId, clientId);
        }
        int serverId = serverTable.c2s(state, kind);
        if (serverId < 0) {
            LOG.fine(() -> "drop C2S " + kind + " @" + state + " (no server id)");
            return null;
        }
        if (kind == PacketIdTable.Packet.HANDSHAKE) {
            return rewriteHandshake(session, body, serverId);
        }
        if (kind == PacketIdTable.Packet.LOGIN_START) {
            return rewriteLoginStart(session, body, serverId);
        }
        if (kind == PacketIdTable.Packet.LOGIN_ACK) {
            session.setState(ConnState.CONFIG);
        }
        if (kind == PacketIdTable.Packet.CONFIG_ACK) {
            session.setState(ConnState.PLAY);
        }
        // Paper 776 client_information requires particle status; older clients omit it.
        if (state == ConnState.CONFIG && clientId == 0x00) {
            ByteBuf modern = rewriteClientInformation(body);
            if (modern != null) {
                return modern;
            }
        }
        if (state == ConnState.CONFIG && ConfigPacketRemapper.needsRemap(session)) {
            ByteBuf cfg = ConfigPacketRemapper.remapC2S(session, clientId, body);
            if (cfg != null) {
                return cfg;
            }
        }
        return rewriteId(body, clientId, serverId);
    }

    /**
     * Rebuild configuration client_information for Paper 26.2.
     * Body is after packet id; returns full packet including id 0x00.
     */
    private static ByteBuf rewriteClientInformation(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            String locale = McCodec.readString(body, 16);
            byte view = body.readByte();
            int chatMode = McCodec.readVarInt(body);
            boolean chatColors = body.readBoolean();
            int skin = body.readUnsignedByte();
            int mainHand = McCodec.readVarInt(body);
            boolean filtering = body.isReadable() && body.readBoolean();
            boolean listing = body.isReadable() && body.readBoolean();
            int particles = 0;
            if (body.isReadable()) {
                particles = McCodec.readVarInt(body);
            }
            ByteBuf out = Unpooled.buffer(48);
            McCodec.writeVarInt(out, 0x00);
            McCodec.writeString(out, locale);
            out.writeByte(view);
            McCodec.writeVarInt(out, chatMode);
            out.writeBoolean(chatColors);
            out.writeByte(skin);
            McCodec.writeVarInt(out, mainHand);
            out.writeBoolean(filtering);
            out.writeBoolean(listing);
            McCodec.writeVarInt(out, particles);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    private ByteBuf transformS2C(ViaSession session, ConnState state, int serverId, ByteBuf body) {
        if (state == ConnState.LOGIN && serverId == 0x02) {
            int clientId = clientTable.s2c(ConnState.LOGIN, PacketIdTable.Packet.LOGIN_SUCCESS);
            if (clientId < 0) {
                clientId = 0x02;
            }
            return LoginSuccessRewriter.rewrite(session, body, clientId);
        }
        // Legacy clients: Paper CONFIG packets are not speakable — auto-ACK bridge.
        if (session.isConfigSkip() && (state == ConnState.CONFIG || state == ConnState.LOGIN)) {
            return handleConfigSkipS2C(session, serverId, body);
        }
        if (state == ConnState.CONFIG && ConfigPacketRemapper.needsRemap(session)) {
            return ConfigPacketRemapper.remapS2C(session, serverId, body);
        }
        if (state == ConnState.PLAY) {
            if (forward != null) {
                ByteBuf f = forward.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, serverId, body);
                if (f != null) {
                    return f;
                }
            }
            if (mid != null) {
                ByteBuf m = mid.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, serverId, body);
                if (m != null) {
                    return m;
                }
                if (mid.serverDump().hasPlay()) {
                    return null;
                }
            }
            if (rewind != null) {
                ByteBuf r = rewind.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, serverId, body);
                if (r != null) {
                    return r;
                }
            }
            ByteBuf play = playRemapper.remap(session, ViaDirection.SERVERBOUND_TO_CLIENT, serverId, body);
            if (play != null) {
                return play;
            }
        }
        PacketIdTable.Packet kind = serverTable.s2cPacket(state, serverId);
        if (kind == null) {
            return rewriteId(body, serverId, serverId);
        }
        int clientId = clientTable.s2c(state, kind);
        if (clientId < 0) {
            LOG.fine(() -> "drop S2C " + kind + " @" + state);
            return null;
        }
        if (kind == PacketIdTable.Packet.LOGIN_COMPRESSION) {
            int mark = body.readerIndex();
            int threshold = 256;
            try {
                threshold = McCodec.readVarInt(body);
            } catch (Exception ignored) {
                // keep default
            }
            body.readerIndex(mark);
            session.enableCompression(threshold);
            // Rebuild explicitly — never forward a body-only slice (clients parse
            // threshold as packet id → "partial packet name=256").
            ByteBuf rebuilt = Unpooled.buffer(8);
            McCodec.writeVarInt(rebuilt, clientId);
            McCodec.writeVarInt(rebuilt, threshold);
            return rebuilt;
        }
        if (kind == PacketIdTable.Packet.LOGIN_SUCCESS) {
            return LoginSuccessRewriter.rewrite(session, body, clientId);
        }
        if (kind == PacketIdTable.Packet.STATUS_RESPONSE) {
            return rewriteStatusResponse(session, body, clientId);
        }
        if (kind == PacketIdTable.Packet.CONFIG_FINISH && !session.clientBand().hasConfigurationPhase()) {
            // Should be handled by config-skip; drop if leaked
            return null;
        }
        return rewriteId(body, serverId, clientId);
    }

    /**
     * Rewrite status JSON so {@code version.protocol} matches the probing client.
     * Otherwise modern clients refuse to log in when Paper advertises 776.
     */
    private ByteBuf rewriteStatusResponse(ViaSession session, ByteBuf bodyAfterId, int clientPacketId) {
        int mark = bodyAfterId.readerIndex();
        try {
            String json = McCodec.readString(bodyAfterId, 32767);
            int clientProto = session.clientProtocol();
            String rewritten = json.replaceAll(
                    "\"protocol\"\\s*:\\s*\\d+",
                    "\"protocol\":" + clientProto);
            // Soft name hint for multi-version
            if (!rewritten.contains("YaPcore") && rewritten.contains("\"name\"")) {
                rewritten = rewritten.replaceFirst(
                        "\"name\"\\s*:\\s*\"[^\"]*\"",
                        "\"name\":\"YaPcore (via " + session.clientBand().name() + ")\"");
            }
            ByteBuf out = Unpooled.buffer(rewritten.length() + 16);
            McCodec.writeVarInt(out, clientPacketId);
            McCodec.writeString(out, rewritten);
            return out;
        } catch (Exception e) {
            bodyAfterId.readerIndex(mark);
            return rewriteId(bodyAfterId, 0x00, clientPacketId);
        }
    }

    /**
     * Drop Paper configuration toward legacy clients; queue auto-replies via session flags
     * inspected by {@link com.yapcore.protocol.via.ViaProxyHandler}.
     */
    private ByteBuf handleConfigSkipS2C(ViaSession session, int serverId, ByteBuf body) {
        // Paper 776 configuration clientbound ids (see protocol/vanilla/26.2/packets.json)
        return switch (serverId) {
            case 0x0E -> { // select_known_packs
                session.noteConfigAutoReply(ViaSession.ConfigAutoReply.KNOWN_PACKS);
                yield null;
            }
            case 0x03 -> { // finish_configuration
                session.noteConfigAutoReply(ViaSession.ConfigAutoReply.FINISH);
                yield null;
            }
            case 0x04, 0x05 -> { // keep_alive / ping — ignore (no client to answer)
                yield null;
            }
            case 0x02 -> { // disconnect — rebuild as login disconnect for legacy if still login
                yield rewriteId(body, serverId, 0x00);
            }
            default -> null; // registry_data, features, tags, cookies, …
        };
    }

    private ByteBuf rewriteHandshake(ViaSession session, ByteBuf bodyAfterId, int serverPacketId) {
        int clientProto = McCodec.readVarInt(bodyAfterId);
        String host = McCodec.readString(bodyAfterId, 255);
        int port = bodyAfterId.readUnsignedShort();
        int intent = McCodec.readVarInt(bodyAfterId);
        ByteBuf out = Unpooled.buffer(64);
        McCodec.writeVarInt(out, serverPacketId);
        McCodec.writeVarInt(out, session.serverProtocol());
        McCodec.writeString(out, host);
        out.writeShort(session.backendPort() > 0 ? session.backendPort() : port);
        McCodec.writeVarInt(out, intent);
        if (intent == 1) {
            session.setState(ConnState.STATUS);
        } else if (intent == 2 || intent == 3) {
            session.setState(ConnState.LOGIN);
        }
        LOG.info("Via handshake clientProto=" + clientProto
                + " → serverProto=" + session.serverProtocol()
                + " band=" + session.clientBand().name()
                + "→" + session.serverBand().name()
                + " intent=" + intent
                + (forward != null ? " forward=4.V1" : "")
                + (mid != null ? " mid=dump" : "")
                + (rewind != null ? " rewind=1.8" : ""));
        return out;
    }

    /**
     * Rebuild login hello for Paper 26.2: username + UUID.
     * Legacy clients send username only (or 1.19 name+hasUuid+uuid) — passthrough breaks Paper.
     */
    private ByteBuf rewriteLoginStart(ViaSession session, ByteBuf bodyAfterId, int serverPacketId) {
        String username = "Player";
        java.util.UUID uuid = null;
        int mark = bodyAfterId.readerIndex();
        try {
            username = McCodec.readString(bodyAfterId, 16);
            if (bodyAfterId.isReadable()) {
                // 1.19: boolean hasUuid + optional uuid; 1.20.2+: bare uuid
                if (bodyAfterId.readableBytes() == 16) {
                    uuid = McCodec.readUuid(bodyAfterId);
                } else if (bodyAfterId.readableBytes() >= 1) {
                    boolean hasUuid = bodyAfterId.readBoolean();
                    if (hasUuid && bodyAfterId.readableBytes() >= 16) {
                        uuid = McCodec.readUuid(bodyAfterId);
                    }
                }
            }
        } catch (Exception e) {
            bodyAfterId.readerIndex(mark);
            try {
                username = McCodec.readString(bodyAfterId, 16);
            } catch (Exception ignored) {
                // keep default
            }
        }
        session.setUsername(username);
        if (uuid == null) {
            uuid = McCodec.offlineUuid(username);
        }
        ByteBuf out = Unpooled.buffer(64);
        McCodec.writeVarInt(out, serverPacketId);
        McCodec.writeString(out, username);
        McCodec.writeUuid(out, uuid);
        LOG.info("Via login_start → modern hello user=" + username + " uuid=" + uuid);
        return out;
    }

    private static ByteBuf rewriteId(ByteBuf bodyAfterOldId, int oldId, int newId) {
        ByteBuf out = Unpooled.buffer(bodyAfterOldId.readableBytes() + 5);
        McCodec.writeVarInt(out, newId);
        out.writeBytes(bodyAfterOldId, bodyAfterOldId.readerIndex(), bodyAfterOldId.readableBytes());
        return out;
    }

    public PlayPacketRemapper playRemapper() {
        return playRemapper;
    }

    public MidBandTransformer mid() {
        return mid;
    }
}
