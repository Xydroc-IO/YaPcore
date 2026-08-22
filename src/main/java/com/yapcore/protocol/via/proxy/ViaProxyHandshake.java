package com.yapcore.protocol.via.proxy;

import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.transform.PacketTransformer;
import io.netty.buffer.ByteBuf;

/**
 * Initial handshake capture for the Via proxy edge.
 */
public final class ViaProxyHandshake {

    public record Result(ViaSession session, PacketTransformer transformer) {
    }

    private ViaProxyHandshake() {
    }

    public static Result capture(ByteBuf packet, int serverProtocol, int backendPort) {
        packet.markReaderIndex();
        try {
            int id = McCodec.readVarInt(packet);
            if (id != 0x00) {
                return null;
            }
            int clientProto = McCodec.readVarInt(packet);
            ViaSession session = new ViaSession(clientProto, serverProtocol, backendPort);
            PacketTransformer transformer = new PacketTransformer(session);
            ProtocolCompat.onJavaJoin("via-pending", clientProto);
            packet.resetReaderIndex();
            return new Result(session, transformer);
        } catch (Exception e) {
            packet.resetReaderIndex();
            return null;
        }
    }
}
