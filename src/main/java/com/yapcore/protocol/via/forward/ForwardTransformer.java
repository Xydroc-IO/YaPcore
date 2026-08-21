package com.yapcore.protocol.via.forward;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.id.PacketIdDump;
import com.yapcore.protocol.via.remap.BlockRemapper;
import com.yapcore.protocol.via.remap.ChunkRemapper;
import com.yapcore.protocol.via.remap.EntityRemapper;
import com.yapcore.protocol.via.remap.ItemRemapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * ViaVersion-equivalent: newer JE client ↔ older Paper server protocol (4.V1).
 * Uses {@link PacketIdDump} for complete play ID remaps when dumps exist.
 */
public final class ForwardTransformer {

    private final ViaSession session;
    private final PacketIdDump clientDump;
    private final PacketIdDump serverDump;
    private final ChunkRemapper chunks;
    private final ItemRemapper items;
    private final EntityRemapper entities;
    private final boolean dumpBacked;

    public ForwardTransformer(ViaSession session) {
        this.session = session;
        this.clientDump = PacketIdDump.forProtocol(session.clientProtocol());
        this.serverDump = PacketIdDump.forProtocol(session.serverProtocol());
        this.dumpBacked = clientDump.hasPlay() && serverDump.hasPlay();
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        BlockRemapper blocks = new BlockRemapper(server, client);
        this.chunks = new ChunkRemapper(server, client, blocks);
        this.items = new ItemRemapper(client, server);
        this.entities = new EntityRemapper(client, server);
    }

    public static boolean applies(ViaSession session) {
        return session.needsForward();
    }

    public ByteBuf transform(ViaSession session, ViaDirection direction, int packetId, ByteBuf body) {
        if (dumpBacked) {
            return switch (direction) {
                case CLIENTBOUND_TO_SERVER -> transformC2SDump(packetId, body);
                case SERVERBOUND_TO_CLIENT -> transformS2CDump(packetId, body);
            };
        }
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        return switch (direction) {
            case CLIENTBOUND_TO_SERVER -> transformC2SLegacy(client, server, packetId, body);
            case SERVERBOUND_TO_CLIENT -> transformS2CLegacy(client, server, packetId, body);
        };
    }

    private ByteBuf transformC2SDump(int clientId, ByteBuf body) {
        int serverId = PacketIdDump.remapPlayC2s(
                session.clientProtocol(), session.serverProtocol(), clientId);
        if (serverId < 0) {
            return null;
        }
        return rewriteId(body, serverId);
    }

    private ByteBuf transformS2CDump(int serverId, ByteBuf body) {
        int clientId = PacketIdDump.remapPlayS2c(
                session.serverProtocol(), session.clientProtocol(), serverId);
        if (clientId < 0) {
            return null;
        }
        String name = serverDump.playS2cName(serverId);
        if (name != null) {
            String n = PacketIdDump.canonicalize(name);
            if (n.contains("map_chunk") || n.contains("level_chunk")) {
                ByteBuf remapped = chunks.remapClientboundChunk(body);
                ByteBuf out = Unpooled.buffer(remapped.readableBytes() + 5);
                McCodec.writeVarInt(out, clientId);
                out.writeBytes(remapped);
                remapped.release();
                return out;
            }
            if (n.equals("spawn_entity") || n.equals("add_entity")) {
                return remapSpawn(body, clientId);
            }
        }
        return rewriteId(body, clientId);
    }

    private ByteBuf transformC2SLegacy(ProtocolBand client, ProtocolBand server, int clientId, ByteBuf body) {
        if (clientId == client.keepAliveSbId()) {
            return remapKeepAlive(server.keepAliveSbId(), body);
        }
        if (clientId == client.playerPositionId() || isLikelyPosition(clientId, client)) {
            return rewriteId(body, server.minProtocol() >= 776 ? 0x1E : server.playerPositionId());
        }
        if (clientId >= 0x08 && clientId <= 0x10) {
            return remapItemTowardServer(body, clientId);
        }
        return null;
    }

    private ByteBuf transformS2CLegacy(ProtocolBand client, ProtocolBand server, int serverId, ByteBuf body) {
        if (serverId == server.keepAliveCbId()) {
            return remapKeepAlive(client.keepAliveCbId(), body);
        }
        if (serverId == server.playerPositionId()) {
            return rewriteId(body, client.playerPositionId());
        }
        if (serverId == server.playLoginId()) {
            return rewriteId(body, client.playLoginId());
        }
        if (serverId == server.levelChunkWithLightId() || isLikelyChunk(serverId)) {
            ByteBuf remapped = chunks.remapClientboundChunk(body);
            ByteBuf out = Unpooled.buffer(remapped.readableBytes() + 5);
            McCodec.writeVarInt(out, client.levelChunkWithLightId());
            out.writeBytes(remapped);
            remapped.release();
            return out;
        }
        if (serverId == server.gameEventId()) {
            return rewriteId(body, client.gameEventId());
        }
        if (server.setCenterChunkId() >= 0 && serverId == server.setCenterChunkId()) {
            int outId = client.setCenterChunkId() >= 0 ? client.setCenterChunkId() : serverId;
            return rewriteId(body, outId);
        }
        if (serverId <= 0x05) {
            return remapSpawn(body, serverId == 0x01 ? 0x01 : serverId);
        }
        return null;
    }

    private ByteBuf remapSpawn(ByteBuf body, int outId) {
        int mark = body.readerIndex();
        try {
            int entityId = McCodec.readVarInt(body);
            long uuidM = body.readLong();
            long uuidL = body.readLong();
            int type = McCodec.readVarInt(body);
            int mapped = entities.toClientType(type);
            double x = body.readDouble();
            double y = body.readDouble();
            double z = body.readDouble();
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outId);
            McCodec.writeVarInt(out, entityId);
            out.writeLong(uuidM);
            out.writeLong(uuidL);
            McCodec.writeVarInt(out, mapped);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, outId);
        }
    }

    private ByteBuf remapItemTowardServer(ByteBuf body, int outId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 8);
            McCodec.writeVarInt(out, outId);
            if (body.readableBytes() >= 4) {
                out.writeShort(body.readShort());
                if (body.readableBytes() >= 2) {
                    int itemId = body.readShort();
                    out.writeShort(items.remapToServer(itemId & 0xFFFF));
                }
            }
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, outId);
        }
    }

    private static boolean isLikelyPosition(int id, ProtocolBand band) {
        return id == band.playerPositionId()
                || (band.minProtocol() >= 766 && id >= 0x40 && id <= 0x50);
    }

    private static boolean isLikelyChunk(int id) {
        return id >= 0x20 && id <= 0x30;
    }

    private static ByteBuf remapKeepAlive(int outId, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long id;
            if (body.readableBytes() >= 8) {
                id = body.readLong();
            } else if (body.readableBytes() >= 4) {
                id = body.readInt();
            } else {
                id = McCodec.readVarInt(body);
            }
            ByteBuf out = Unpooled.buffer(16);
            McCodec.writeVarInt(out, outId);
            out.writeLong(id);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, outId);
        }
    }

    private static ByteBuf rewriteId(ByteBuf bodyAfterOldId, int newId) {
        ByteBuf out = Unpooled.buffer(bodyAfterOldId.readableBytes() + 5);
        McCodec.writeVarInt(out, newId);
        out.writeBytes(bodyAfterOldId, bodyAfterOldId.readerIndex(), bodyAfterOldId.readableBytes());
        return out;
    }
}
