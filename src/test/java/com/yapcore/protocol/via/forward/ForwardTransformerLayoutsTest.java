package com.yapcore.protocol.via.forward;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ForwardTransformerLayoutsTest {

    @Test
    void keepaliveAndChunkLayoutsForFutureClient() {
        ViaSession session = new ViaSession(800, 776);
        ForwardTransformer ft = new ForwardTransformer(session);
        ProtocolBand server = session.serverBand();
        ProtocolBand client = session.clientBand();

        ByteBuf kaBody = Unpooled.buffer();
        kaBody.writeLong(99L);
        ByteBuf ka = ft.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT,
                server.keepAliveCbId(), kaBody);
        assertNotNull(ka);
        assertEquals(client.keepAliveCbId(), McCodec.readVarInt(ka));
        assertEquals(99L, ka.readLong());
        ka.release();
        kaBody.release();

        ByteBuf chunkBody = Unpooled.buffer();
        chunkBody.writeInt(0);
        chunkBody.writeInt(0);
        chunkBody.writeByte(0x0a);
        chunkBody.writeShort(0);
        chunkBody.writeByte(0x00);
        McCodec.writeVarInt(chunkBody, 0);
        McCodec.writeVarInt(chunkBody, 0);
        ByteBuf chunk = ft.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT,
                server.levelChunkWithLightId(), chunkBody);
        assertNotNull(chunk);
        assertEquals(client.levelChunkWithLightId(), McCodec.readVarInt(chunk));
        chunk.release();
        chunkBody.release();
    }
}
