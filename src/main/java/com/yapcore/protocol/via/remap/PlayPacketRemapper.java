package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.id.PacketIdTable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * PLAY-state remaps: packet IDs from {@link PacketIdTable} + item/block/entity/chunk.
 */
public final class PlayPacketRemapper {

    private final PacketIdTable clientTable;
    private final PacketIdTable serverTable;
    private final ItemRemapper items;
    private final BlockRemapper blocks;
    private final EntityRemapper entities;
    private final ChunkRemapper chunks;

    public PlayPacketRemapper(ViaSession session) {
        ProtocolBand from = session.clientBand();
        ProtocolBand to = session.serverBand();
        this.clientTable = PacketIdTable.forBand(from);
        this.serverTable = PacketIdTable.forBand(to);
        this.items = new ItemRemapper(from, to);
        this.blocks = new BlockRemapper(from, to);
        this.entities = new EntityRemapper(from, to);
        this.chunks = new ChunkRemapper(from, to, blocks);
    }

    public ByteBuf remap(ViaSession session, ViaDirection direction, int packetId, ByteBuf body) {
        if (session.state() != ConnState.PLAY || !session.needsRemap()) {
            return null;
        }
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        return switch (direction) {
            case CLIENTBOUND_TO_SERVER -> remapC2S(client, server, packetId, body);
            case SERVERBOUND_TO_CLIENT -> remapS2C(client, server, packetId, body);
        };
    }

    private ByteBuf remapC2S(ProtocolBand client, ProtocolBand server, int clientId, ByteBuf body) {
        PacketIdTable.Packet kind = clientTable.c2sPacket(ConnState.PLAY, clientId);
        if (kind != null) {
            int serverId = serverTable.c2s(ConnState.PLAY, kind);
            if (serverId < 0) {
                return null;
            }
            if (kind == PacketIdTable.Packet.PLAY_SET_SLOT || kind == PacketIdTable.Packet.PLAY_WINDOW_ITEMS) {
                return remapItemPayloadC2S(body, serverId);
            }
            return rewriteId(body, serverId);
        }
        if (isLikelySlotPacket(clientId)) {
            return remapItemPayloadC2S(body, server.keepAliveSbId());
        }
        return rewriteId(body, clientId);
    }

    private ByteBuf remapS2C(ProtocolBand client, ProtocolBand server, int serverId, ByteBuf body) {
        PacketIdTable.Packet kind = serverTable.s2cPacket(ConnState.PLAY, serverId);
        if (kind == PacketIdTable.Packet.PLAY_LEVEL_CHUNK
                || (kind == null && isLikelyChunkPacket(serverId, body))) {
            ByteBuf chunkBody = chunks.remapClientboundChunk(body);
            int outId = kind != null
                    ? clientTable.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_LEVEL_CHUNK)
                    : mapPlayS2C(client, server, serverId);
            if (outId < 0) {
                outId = client.levelChunkWithLightId();
            }
            ByteBuf out = Unpooled.buffer(chunkBody.readableBytes() + 5);
            McCodec.writeVarInt(out, outId);
            out.writeBytes(chunkBody);
            chunkBody.release();
            return out;
        }
        if (kind == PacketIdTable.Packet.PLAY_SPAWN_ENTITY
                || (kind == null && isLikelySpawnEntity(serverId))) {
            return remapSpawnS2C(client, server, serverId, body);
        }
        if (kind == PacketIdTable.Packet.PLAY_SET_SLOT
                || kind == PacketIdTable.Packet.PLAY_WINDOW_ITEMS) {
            int clientId = clientTable.s2c(ConnState.PLAY, kind);
            return remapItemPayloadS2C(body, clientId < 0 ? serverId : clientId);
        }
        if (kind != null) {
            int clientId = clientTable.s2c(ConnState.PLAY, kind);
            if (clientId < 0) {
                return null;
            }
            return rewriteId(body, clientId);
        }
        return rewriteId(body, mapPlayS2C(client, server, serverId));
    }

    private ByteBuf remapItemPayloadC2S(ByteBuf body, int newId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 8);
            McCodec.writeVarInt(out, newId);
            if (body.readableBytes() >= 2) {
                out.writeShort(body.readShort());
            }
            if (body.readableBytes() >= 2) {
                short itemId = body.readShort();
                out.writeShort(items.remapToServer(itemId & 0xFFFF));
            }
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, newId);
        }
    }

    private ByteBuf remapItemPayloadS2C(ByteBuf body, int newId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 8);
            McCodec.writeVarInt(out, newId);
            if (body.readableBytes() >= 2) {
                out.writeShort(body.readShort());
            }
            if (body.readableBytes() >= 2) {
                short itemId = body.readShort();
                out.writeShort(items.remapToClient(itemId & 0xFFFF));
            }
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, newId);
        }
    }

    private ByteBuf remapSpawnS2C(ProtocolBand client, ProtocolBand server, int serverId, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int eid = McCodec.readVarInt(body);
            long uuidMsb = 0;
            long uuidLsb = 0;
            boolean hasUuid = client.ordinal() >= ProtocolBand.V1_9.ordinal();
            if (body.readableBytes() >= 16) {
                uuidMsb = body.readLong();
                uuidLsb = body.readLong();
            }
            int type = McCodec.readVarInt(body);
            int remappedType = entities.toClientType(type);
            int outId = clientTable.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_SPAWN_ENTITY);
            if (outId < 0) {
                outId = mapPlayS2C(client, server, serverId);
            }
            ByteBuf out = Unpooled.buffer(64 + body.readableBytes());
            McCodec.writeVarInt(out, outId);
            McCodec.writeVarInt(out, eid);
            if (hasUuid) {
                out.writeLong(uuidMsb);
                out.writeLong(uuidLsb);
            }
            McCodec.writeVarInt(out, remappedType);
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, mapPlayS2C(client, server, serverId));
        }
    }

    private static boolean isLikelySlotPacket(int id) {
        return id >= 0x08 && id <= 0x0E;
    }

    private static boolean isLikelyChunkPacket(int id, ByteBuf body) {
        return body.readableBytes() > 256 && id >= 0x20 && id <= 0x30;
    }

    private static boolean isLikelySpawnEntity(int id) {
        return id >= 0x00 && id <= 0x05;
    }

    private static int mapPlayS2C(ProtocolBand client, ProtocolBand server, int id) {
        if (id == server.keepAliveCbId()) {
            return client.keepAliveCbId();
        }
        if (id == server.playerPositionId()) {
            return client.playerPositionId();
        }
        if (id == server.playLoginId()) {
            return client.playLoginId();
        }
        if (id == server.levelChunkWithLightId()) {
            return client.levelChunkWithLightId();
        }
        return id;
    }

    private static ByteBuf rewriteId(ByteBuf bodyAfterOldId, int newId) {
        ByteBuf out = Unpooled.buffer(bodyAfterOldId.readableBytes() + 5);
        McCodec.writeVarInt(out, newId);
        out.writeBytes(bodyAfterOldId, bodyAfterOldId.readerIndex(), bodyAfterOldId.readableBytes());
        return out;
    }

    public ItemRemapper items() {
        return items;
    }

    public BlockRemapper blocks() {
        return blocks;
    }

    public EntityRemapper entities() {
        return entities;
    }
}
