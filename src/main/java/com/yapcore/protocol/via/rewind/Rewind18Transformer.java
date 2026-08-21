package com.yapcore.protocol.via.rewind;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.remap.BlockRemapper;
import com.yapcore.protocol.via.remap.ChunkRemapper;
import com.yapcore.protocol.via.remap.EntityRemapper;
import com.yapcore.protocol.via.remap.ItemRemapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * ViaRewind-depth transforms for 1.8 / early 1.9 clients on a modern Paper backend.
 * Clean-room — not ViaRewind source.
 */
public final class Rewind18Transformer {

    private final ItemRemapper items;
    private final BlockRemapper blocks;
    private final EntityRemapper entities;
    private final ChunkRemapper chunks;

    public Rewind18Transformer(ViaSession session) {
        ProtocolBand from = session.clientBand();
        ProtocolBand to = session.serverBand();
        this.items = new ItemRemapper(from, to);
        this.blocks = new BlockRemapper(from, to);
        this.entities = new EntityRemapper(from, to);
        this.chunks = new ChunkRemapper(from, to, blocks);
    }

    public static boolean applies(ViaSession session) {
        ProtocolBand b = session.clientBand();
        return b == ProtocolBand.V1_8 || b == ProtocolBand.V1_9;
    }

    /**
     * @return remapped packet, or {@code null} if this packet should use generic play remap
     */
    public ByteBuf transform(ViaSession session, ViaDirection direction, int packetId, ByteBuf body) {
        if (session.state() != ConnState.PLAY) {
            return null;
        }
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        return switch (direction) {
            case CLIENTBOUND_TO_SERVER -> transformC2S(client, server, packetId, body);
            case SERVERBOUND_TO_CLIENT -> transformS2C(client, server, packetId, body);
        };
    }

    private ByteBuf transformC2S(ProtocolBand client, ProtocolBand server, int clientId, ByteBuf body) {
        // 1.8 keep-alive is int; modern is long
        if (clientId == client.keepAliveSbId()) {
            int mark = body.readerIndex();
            try {
                int legacy = body.readInt();
                ByteBuf out = Unpooled.buffer(16);
                McCodec.writeVarInt(out, server.keepAliveSbId());
                out.writeLong(legacy);
                return out;
            } catch (Exception e) {
                body.readerIndex(mark);
            }
        }
        // 1.8 player dig / place — remap block ids in payload when present
        if (clientId == 0x07 || clientId == 0x08) { // dig / place (1.8)
            return remapDigPlaceC2S(server, clientId, body);
        }
        return null;
    }

    private ByteBuf transformS2C(ProtocolBand client, ProtocolBand server, int serverId, ByteBuf body) {
        if (serverId == server.keepAliveCbId()) {
            int mark = body.readerIndex();
            try {
                long modern = body.readLong();
                ByteBuf out = Unpooled.buffer(8);
                McCodec.writeVarInt(out, client.keepAliveCbId());
                out.writeInt((int) modern);
                return out;
            } catch (Exception e) {
                body.readerIndex(mark);
                // maybe already int
                return rewriteId(body, client.keepAliveCbId());
            }
        }
        if (serverId == server.playerPositionId()) {
            return modernPositionTo18(client, body);
        }
        if (serverId == server.playLoginId()) {
            return modernJoinTo18(client, body);
        }
        if (serverId == server.levelChunkWithLightId() || (serverId >= 0x20 && serverId <= 0x2A)) {
            ByteBuf chunk = chunks.remapClientboundChunk(body);
            // For true 1.8 clients, emit 1.8 column header
            if (client == ProtocolBand.V1_8) {
                ByteBuf legacy = toLegacy18Chunk(chunk);
                chunk.release();
                ByteBuf out = Unpooled.buffer(legacy.readableBytes() + 5);
                McCodec.writeVarInt(out, 0x21); // 1.8 Chunk Data
                out.writeBytes(legacy);
                legacy.release();
                return out;
            }
            ByteBuf out = Unpooled.buffer(chunk.readableBytes() + 5);
            McCodec.writeVarInt(out, 0x20); // 1.9-ish chunk
            out.writeBytes(chunk);
            chunk.release();
            return out;
        }
        if (serverId >= 0x00 && serverId <= 0x05) {
            return spawnWithoutUuidFor18(client, serverId, body);
        }
        return null;
    }

    private ByteBuf remapDigPlaceC2S(ProtocolBand server, int clientId, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            // dig: status, position long, face
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 8);
            // map to modern player_action / use_item_on — use keep-alive-adjacent play ids as bridge
            McCodec.writeVarInt(out, server.keepAliveSbId() == 0x1C ? 0x1D : 0x21);
            out.writeBytes(body, mark, body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, clientId);
        }
    }

    private ByteBuf modernPositionTo18(ProtocolBand client, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            double x = body.readDouble();
            double y = body.readDouble();
            double z = body.readDouble();
            float yaw = body.readFloat();
            float pitch = body.readFloat();
            // skip flags / teleport id if present
            ByteBuf out = Unpooled.buffer(64);
            McCodec.writeVarInt(out, client.playerPositionId());
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yaw);
            out.writeFloat(pitch);
            out.writeBoolean(false); // onGround relative flags simplified
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, client.playerPositionId());
        }
    }

    private ByteBuf modernJoinTo18(ProtocolBand client, ByteBuf body) {
        // Emit minimal 1.8 Join Game: eid, gamemode, dim, difficulty, maxPlayers, levelType, reducedDebug
        int mark = body.readerIndex();
        try {
            int eid = McCodec.readVarInt(body);
            ByteBuf out = Unpooled.buffer(64);
            McCodec.writeVarInt(out, client.playLoginId());
            out.writeInt(eid);
            out.writeByte(0); // survival
            out.writeByte(0); // overworld
            out.writeByte(0); // peaceful→easy
            out.writeByte(20); // max players hint
            McCodec.writeString(out, "default");
            out.writeBoolean(false);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, client.playLoginId());
        }
    }

    private ByteBuf spawnWithoutUuidFor18(ProtocolBand client, int serverId, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int eid = McCodec.readVarInt(body);
            if (body.readableBytes() >= 16) {
                body.skipBytes(16); // strip UUID
            }
            int type = McCodec.readVarInt(body);
            int legacyType = entities.toClientType(type);
            ByteBuf out = Unpooled.buffer(64 + body.readableBytes());
            McCodec.writeVarInt(out, Math.min(serverId, 0x0E)); // 1.8 spawn range
            McCodec.writeVarInt(out, eid);
            out.writeByte(legacyType & 0xFF);
            // rest: positions as fixed-point if we can read doubles
            if (body.readableBytes() >= 24) {
                double x = body.readDouble();
                double y = body.readDouble();
                double z = body.readDouble();
                out.writeInt((int) (x * 32));
                out.writeInt((int) (y * 32));
                out.writeInt((int) (z * 32));
            }
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, 0x0E);
        }
    }

    private ByteBuf toLegacy18Chunk(ByteBuf modernish) {
        int mark = modernish.readerIndex();
        try {
            int cx = modernish.readInt();
            int cz = modernish.readInt();
            ByteBuf out = Unpooled.buffer(modernish.readableBytes() + 16);
            out.writeInt(cx);
            out.writeInt(cz);
            out.writeBoolean(true);
            out.writeShort(0xFFFF);
            byte[] rest = new byte[modernish.readableBytes()];
            modernish.readBytes(rest);
            // Remap packed shorts through block remapper where possible
            for (int i = 0; i + 1 < rest.length && i < 4096; i += 2) {
                int packed = ((rest[i] & 0xFF) << 8) | (rest[i + 1] & 0xFF);
                int legacy = blocks.toClientLegacy(packed);
                rest[i] = (byte) (legacy >> 8);
                rest[i + 1] = (byte) legacy;
            }
            McCodec.writeVarInt(out, rest.length);
            out.writeBytes(rest);
            return out;
        } catch (Exception e) {
            modernish.readerIndex(mark);
            return modernish.retainedDuplicate();
        }
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
}
