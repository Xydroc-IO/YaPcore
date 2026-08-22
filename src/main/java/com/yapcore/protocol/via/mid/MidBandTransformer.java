package com.yapcore.protocol.via.mid;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.id.PacketIdDump;
import com.yapcore.protocol.via.id.PacketIdRemapTable;
import com.yapcore.protocol.via.remap.BlockRemapper;
import com.yapcore.protocol.via.remap.ChunkLightCodec;
import com.yapcore.protocol.via.remap.ChunkRemapper;
import com.yapcore.protocol.via.remap.EntityRemapper;
import com.yapcore.protocol.via.remap.ItemRemapper;
import com.yapcore.protocol.via.remap.SlotCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Completes ViaBackwards-class paths for modern mid bands (764–775) ↔ Paper 776.
 * Remaps <b>every</b> play packet ID by name via {@link PacketIdDump}; handles
 * login session-UUID layout and join-critical body reshapes (slots, equipment, metadata).
 * <p>
 * Applies when client &lt; server and dumps exist for both (covers 764–765 and 774→776).
 */
public final class MidBandTransformer {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaMid");

    private final ViaSession session;
    private final PacketIdDump clientDump;
    private final PacketIdDump serverDump;
    /** server play S2C id → client play S2C id */
    private final PacketIdRemapTable s2cServerToClient;
    /** client play C2S id → server play C2S id */
    private final PacketIdRemapTable c2sClientToServer;
    private final ChunkRemapper chunks;
    private final ItemRemapper items;
    private final EntityRemapper entities;

    public MidBandTransformer(ViaSession session) {
        this.session = session;
        this.clientDump = PacketIdDump.forProtocol(session.clientProtocol());
        this.serverDump = PacketIdDump.forProtocol(session.serverProtocol());
        this.s2cServerToClient = PacketIdRemapTable.playS2c(serverDump, clientDump);
        this.c2sClientToServer = PacketIdRemapTable.playC2s(clientDump, serverDump);
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        BlockRemapper blocks = new BlockRemapper(server, client);
        this.chunks = new ChunkRemapper(server, client, blocks);
        this.items = new ItemRemapper(client, server);
        this.entities = new EntityRemapper(client, server);
    }

    public static boolean applies(ViaSession session) {
        if (!session.needsBackwards()) {
            return false;
        }
        // Rewind owns ≤1.9; Mid owns dump-backed modern (and any band with dumps)
        if (session.clientBand().ordinal() <= ProtocolBand.V1_9.ordinal()) {
            return false;
        }
        PacketIdDump client = PacketIdDump.forProtocol(session.clientProtocol());
        PacketIdDump server = PacketIdDump.forProtocol(session.serverProtocol());
        return client.hasPlay() && server.hasPlay();
    }

    /**
     * @return transformed packet, or {@code null} to fall through
     */
    public ByteBuf transform(ViaSession session, ViaDirection direction, int packetId, ByteBuf body) {
        return switch (direction) {
            case CLIENTBOUND_TO_SERVER -> transformC2S(packetId, body);
            case SERVERBOUND_TO_CLIENT -> transformS2C(packetId, body);
        };
    }

    /** LOGIN-state S2C (login_finished / success) session UUID strip when needed. */
    public ByteBuf transformLoginS2C(int serverId, ByteBuf body) {
        ProtocolBand client = session.clientBand();
        ProtocolBand server = session.serverBand();
        // login_finished is id 0x02 on both modern bands
        if (serverId != 0x02) {
            return null;
        }
        if (server.loginIncludesSessionId() == client.loginIncludesSessionId()) {
            return rewriteId(body, 0x02);
        }
        if (server.loginIncludesSessionId() && !client.loginIncludesSessionId()) {
            return stripLoginSessionUuid(body);
        }
        if (!server.loginIncludesSessionId() && client.loginIncludesSessionId()) {
            return appendLoginSessionUuid(body);
        }
        return rewriteId(body, 0x02);
    }

    private ByteBuf transformC2S(int clientId, ByteBuf body) {
        int serverId = c2sClientToServer.remap(clientId);
        if (serverId < 0) {
            LOG.fine(() -> "mid drop unknown C2S id=0x" + Integer.toHexString(clientId)
                    + " " + session.clientBand() + "→" + session.serverBand());
            return null; // drop — never same-ID passthrough across bands
        }
        String name = clientDump.playC2sName(clientId);
        if (name != null) {
            String n = PacketIdDump.canonicalize(name);
            if (isSlot(n) || n.contains("click") || n.contains("creative")) {
                return remapInventoryPacket(n, body, serverId, true);
            }
        }
        return rewriteId(body, serverId);
    }

    private ByteBuf transformS2C(int serverId, ByteBuf body) {
        int clientId = s2cServerToClient.remap(serverId);
        if (clientId < 0) {
            LOG.fine(() -> "mid drop unknown S2C id=0x" + Integer.toHexString(serverId)
                    + " " + session.serverBand() + "→" + session.clientBand());
            return null;
        }
        String name = serverDump.playS2cName(serverId);
        if (name == null) {
            return rewriteId(body, clientId);
        }
        String n = PacketIdDump.canonicalize(name);
        if (isChunk(n)) {
            ByteBuf remapped = chunks.remapClientboundChunk(body);
            ByteBuf out = Unpooled.buffer(remapped.readableBytes() + 5);
            McCodec.writeVarInt(out, clientId);
            out.writeBytes(remapped);
            remapped.release();
            return out;
        }
        if (isSpawn(n)) {
            return remapSpawn(body, clientId);
        }
        if (ChunkLightCodec.isLightPacket(n)) {
            // Same BitSet + array layout 764–776 — ID remap only
            return rewriteId(body, clientId);
        }
        if (isSlot(n) || isEquipment(n) || isMetadata(n)) {
            return remapInventoryPacket(n, body, clientId, false);
        }
        return rewriteId(body, clientId);
    }

    private static boolean isChunk(String n) {
        return n.contains("map_chunk") || n.contains("level_chunk") || n.equals("chunk_data");
    }

    private static boolean isSpawn(String n) {
        return n.equals("spawn_entity") || n.equals("add_entity") || n.equals("named_entity_spawn");
    }

    private static boolean isSlot(String n) {
        return n.equals("set_slot") || n.equals("container_set_slot")
                || n.equals("window_items") || n.equals("container_set_content");
    }

    private static boolean isEquipment(String n) {
        return n.equals("set_equipment") || n.equals("entity_equipment");
    }

    private static boolean isMetadata(String n) {
        return n.equals("set_entity_data") || n.equals("entity_metadata");
    }

    private ByteBuf remapSpawn(ByteBuf body, int outId) {
        int mark = body.readerIndex();
        try {
            int eid = McCodec.readVarInt(body);
            long uuidM = body.readLong();
            long uuidL = body.readLong();
            int type = McCodec.readVarInt(body);
            int mapped = entities.toClientType(type);
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outId);
            McCodec.writeVarInt(out, eid);
            out.writeLong(uuidM);
            out.writeLong(uuidL);
            McCodec.writeVarInt(out, mapped);
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, outId);
        }
    }

    /**
     * Inventory / equipment / metadata item remaps via {@link SlotCodec}
     * (full window_items walk, NBT↔components for 764–765 ↔ 776).
     */
    private ByteBuf remapInventoryPacket(String n, ByteBuf body, int outId, boolean towardServer) {
        ProtocolBand src = towardServer ? session.clientBand() : session.serverBand();
        ProtocolBand dst = towardServer ? session.serverBand() : session.clientBand();
        SlotCodec slots = new SlotCodec(src, dst, items, !towardServer,
                towardServer ? session.clientProtocol() : session.serverProtocol(),
                towardServer ? session.serverProtocol() : session.clientProtocol());
        ByteBuf remapped;
        if (n.equals("window_items") || n.equals("container_set_content")) {
            remapped = slots.remapWindowItems(body, outId);
        } else if (isEquipment(n)) {
            remapped = slots.remapEquipment(body, outId);
        } else if (isMetadata(n)) {
            remapped = slots.remapEntityMetadata(body, outId);
        } else if (n.equals("set_slot") || n.equals("container_set_slot")) {
            remapped = slots.remapSetSlot(body, outId);
        } else if (n.contains("click") && !n.contains("button")) {
            remapped = slots.remapWindowClick(body, outId);
        } else if (n.contains("creative")) {
            remapped = slots.remapCreativeSlot(body, outId);
        } else {
            return rewriteId(body, outId);
        }
        if (remapped == null) {
            return rewriteId(body, outId);
        }
        return remapped;
    }

    private static ByteBuf stripLoginSessionUuid(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            UUID uuid = McCodec.readUuid(body);
            String name = McCodec.readString(body, 16);
            int props = McCodec.readVarInt(body);
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, 0x02);
            McCodec.writeUuid(out, uuid);
            McCodec.writeString(out, name);
            McCodec.writeVarInt(out, props);
            for (int i = 0; i < props; i++) {
                McCodec.writeString(out, McCodec.readString(body, 32767));
                McCodec.writeString(out, McCodec.readString(body, 32767));
                boolean sig = body.readBoolean();
                out.writeBoolean(sig);
                if (sig) {
                    McCodec.writeString(out, McCodec.readString(body, 32767));
                }
            }
            // skip session uuid on server body
            if (body.readableBytes() >= 16) {
                body.skipBytes(16);
            }
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, 0x02);
        }
    }

    private static ByteBuf appendLoginSessionUuid(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 24);
            McCodec.writeVarInt(out, 0x02);
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
            McCodec.writeUuid(out, UUID.randomUUID());
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return rewriteId(body, 0x02);
        }
    }

    private static ByteBuf rewriteId(ByteBuf bodyAfterOldId, int newId) {
        ByteBuf out = Unpooled.buffer(bodyAfterOldId.readableBytes() + 5);
        McCodec.writeVarInt(out, newId);
        out.writeBytes(bodyAfterOldId, bodyAfterOldId.readerIndex(), bodyAfterOldId.readableBytes());
        return out;
    }

    public PacketIdDump clientDump() {
        return clientDump;
    }

    public PacketIdDump serverDump() {
        return serverDump;
    }

    /** Coverage: fraction of server S2C play packet <em>ids</em> that map to a client id. */
    public double s2cCoverage() {
        if (!serverDump.hasPlay() || !clientDump.hasPlay()) {
            return 0;
        }
        java.util.HashSet<Integer> ids = new java.util.HashSet<>(serverDump.playS2cNames().values());
        int ok = 0;
        for (int id : ids) {
            if (s2cServerToClient.remap(id) >= 0) {
                ok++;
            }
        }
        return ids.isEmpty() ? 0 : (double) ok / ids.size();
    }
}
