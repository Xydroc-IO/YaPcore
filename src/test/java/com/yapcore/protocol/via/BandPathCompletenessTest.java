package com.yapcore.protocol.via;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.id.PacketIdDump;
import com.yapcore.protocol.via.id.PacketIdTable;
import com.yapcore.protocol.via.mid.MidBandTransformer;
import com.yapcore.protocol.via.transform.PacketTransformer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every client band → Paper 776 must have a complete transformer path.
 * 774→776 is the mid-path that was previously ID-shim only.
 */
class BandPathCompletenessTest {

    private static final int PAPER = 776;

    @Test
    void dumpsExistForModernProtocols() {
        for (int proto : new int[]{767, 769, 771, 773, 774, 775, 776}) {
            PacketIdDump dump = PacketIdDump.forProtocol(proto);
            assertTrue(dump.hasPlay(), "dump missing for " + proto);
            assertTrue(dump.playS2cId("keep_alive") >= 0, "keep_alive S2C @" + proto);
            assertTrue(dump.playS2cId("login") >= 0, "login S2C @" + proto);
            assertTrue(dump.playC2sId("keep_alive") >= 0, "keep_alive C2S @" + proto);
        }
    }

    @Test
    void path774to776RemapsJoinCriticalPackets() {
        ViaSession session = new ViaSession(774, PAPER);
        assertEquals(ProtocolBand.V1_21_11, session.clientBand());
        assertTrue(session.needsBackwards());
        assertTrue(MidBandTransformer.applies(session));

        MidBandTransformer mid = new MidBandTransformer(session);
        assertTrue(mid.s2cCoverage() > 0.85, "S2C name coverage was " + mid.s2cCoverage());

        // login 49 → 48
        assertEquals(48, PacketIdDump.remapPlayS2c(PAPER, 774, 49));
        // keepalive 44 → 43
        assertEquals(43, PacketIdDump.remapPlayS2c(PAPER, 774, 44));
        // position 72 → 70
        assertEquals(70, PacketIdDump.remapPlayS2c(PAPER, 774, 72));
        // map_chunk 45 → 44
        assertEquals(44, PacketIdDump.remapPlayS2c(PAPER, 774, 45));
        // set_slot 20 → 20
        assertEquals(20, PacketIdDump.remapPlayS2c(PAPER, 774, 20));
        // C2S position 29 → 30
        assertEquals(30, PacketIdDump.remapPlayC2s(774, PAPER, 29));
        // C2S keep_alive 27 → 28
        assertEquals(28, PacketIdDump.remapPlayC2s(774, PAPER, 27));
    }

    @Test
    void transformer774RewritesLoginAndKeepAlive() {
        ViaSession session = new ViaSession(774, PAPER);
        session.setState(ConnState.PLAY);
        PacketTransformer xf = new PacketTransformer(session);
        assertNotNull(xf.mid());

        ByteBuf keepBody = Unpooled.buffer();
        keepBody.writeLong(12345L);
        ByteBuf framed = Unpooled.buffer();
        McCodec.writeVarInt(framed, 44); // 26.2 keep_alive
        framed.writeBytes(keepBody);
        keepBody.release();

        ByteBuf out = xf.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, framed);
        assertNotNull(out);
        assertEquals(43, McCodec.readVarInt(out)); // 774 keep_alive
        assertEquals(12345L, out.readLong());
        out.release();
        framed.release();
    }

    @Test
    void loginSuccessStripsSessionUuidFor774() {
        ViaSession session = new ViaSession(774, PAPER);
        session.setState(ConnState.LOGIN);
        MidBandTransformer mid = new MidBandTransformer(session);

        ByteBuf body = Unpooled.buffer();
        McCodec.writeUuid(body, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        McCodec.writeString(body, "Steve");
        McCodec.writeVarInt(body, 0); // properties
        McCodec.writeUuid(body, UUID.randomUUID()); // session — 776 only

        ByteBuf out = mid.transformLoginS2C(0x02, body);
        assertNotNull(out);
        assertEquals(0x02, McCodec.readVarInt(out));
        McCodec.readUuid(out);
        assertEquals("Steve", McCodec.readString(out, 16));
        assertEquals(0, McCodec.readVarInt(out));
        assertEquals(0, out.readableBytes(), "session uuid must be stripped for 774");
        out.release();
        body.release();
    }

    @Test
    void everyBandHasTransformerPathTo776() {
        for (ProtocolBand band : ProtocolBand.values()) {
            if (band == ProtocolBand.V26_2) {
                continue;
            }
            int clientProto = band == ProtocolBand.V_FUTURE ? 800 : band.maxProtocol();
            ViaSession session = new ViaSession(clientProto, PAPER);
            if (!session.needsRemap()) {
                continue;
            }
            PacketTransformer xf = new PacketTransformer(session);
            boolean hasPath = xf.mid() != null
                    || session.needsForward()
                    || session.clientBand().ordinal() <= ProtocolBand.V1_9.ordinal()
                    || PacketIdTable.forBand(band).s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_KEEP_ALIVE) >= 0;
            assertTrue(hasPath, "no Via path for " + band + " (proto " + clientProto + ")");

            if (band.minProtocol() >= 766 && band != ProtocolBand.V_FUTURE) {
                assertTrue(MidBandTransformer.applies(session)
                                || session.needsForward(),
                        "modern band " + band + " must use Mid or Forward");
                if (MidBandTransformer.applies(session)) {
                    MidBandTransformer mid = new MidBandTransformer(session);
                    assertTrue(mid.s2cCoverage() > 0.80,
                            band + " S2C coverage " + mid.s2cCoverage());
                }
            }
        }
    }

    @Test
    void packetIdTable776MatchesDump() {
        PacketIdTable t = PacketIdTable.forBand(ProtocolBand.V26_2);
        assertEquals(44, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_KEEP_ALIVE));
        assertEquals(49, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_LOGIN));
        assertEquals(72, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_POSITION));
        assertEquals(45, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_LEVEL_CHUNK));
        assertEquals(20, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_SET_SLOT));
        assertEquals(18, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_WINDOW_ITEMS));
        assertEquals(28, t.c2s(ConnState.PLAY, PacketIdTable.Packet.PLAY_KEEP_ALIVE));
        assertEquals(30, t.c2s(ConnState.PLAY, PacketIdTable.Packet.PLAY_POSITION));
    }

    @Test
    void packetIdTable774MatchesDump() {
        PacketIdTable t = PacketIdTable.forBand(ProtocolBand.V1_21_11);
        assertEquals(43, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_KEEP_ALIVE));
        assertEquals(48, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_LOGIN));
        assertEquals(70, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_POSITION));
        assertEquals(44, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_LEVEL_CHUNK));
        assertEquals(92, t.s2c(ConnState.PLAY, PacketIdTable.Packet.PLAY_SET_CENTER_CHUNK));
        assertEquals(27, t.c2s(ConnState.PLAY, PacketIdTable.Packet.PLAY_KEEP_ALIVE));
        assertEquals(29, t.c2s(ConnState.PLAY, PacketIdTable.Packet.PLAY_POSITION));
    }
}
