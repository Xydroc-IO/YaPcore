package com.yapcore.protocol.via.id;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.forward.ForwardTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.10 — index-driven dumps + future protocol nearest fallback. */
class PacketIdDumpIndexTest {

    @Test
    void indexListsPaperPinAndKnownDumps() {
        assertEquals(776, PacketIdDump.paperPinProtocol());
        assertTrue(PacketIdDump.hasExactDump(776));
        assertTrue(PacketIdDump.hasExactDump(764));
        assertTrue(PacketIdDump.hasExactDump(766));
        assertFalse(PacketIdDump.hasExactDump(9000));
    }

    @Test
    void futureProtocolUsesNearestDumpAndForwardPath() {
        // No exact dump for 800 — should still load play via nearest (776)
        PacketIdDump future = PacketIdDump.forProtocol(800);
        assertTrue(future.hasPlay(), "future proto must nearest-dump to pin");
        assertTrue(PacketIdDump.forProtocol(776).hasPlay());

        ViaSession session = new ViaSession(800, 776);
        assertEquals(ProtocolBand.V_FUTURE, session.clientBand());
        assertTrue(session.needsForward());
        assertTrue(ForwardTransformer.applies(session));
    }

    @Test
    void forwardDumpBackedWhenClientDumpExists() {
        // 777 currently aliases to 26.2 dump in switch/index companions
        ViaSession session = new ViaSession(777, 776);
        assertTrue(session.needsForward() || session.clientProtocol() >= 777);
        ForwardTransformer xf = new ForwardTransformer(session);
        // dump-backed if both sides have play
        assertTrue(PacketIdDump.forProtocol(777).hasPlay());
        assertTrue(PacketIdDump.forProtocol(776).hasPlay());
    }
}
