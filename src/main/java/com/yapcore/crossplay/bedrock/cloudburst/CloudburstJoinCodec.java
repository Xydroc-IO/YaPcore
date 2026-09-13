package com.yapcore.crossplay.bedrock.cloudburst;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * @deprecated Prefer {@link CloudburstSession} + {@link CloudburstPackets}.
 * Thin delegates kept for older call sites.
 */
@Deprecated
public final class CloudburstJoinCodec {
    private CloudburstJoinCodec() {}

    public static BedrockCodec codecFor(int clientProtocol) {
        return CloudburstCodecIndex.codecFor(clientProtocol);
    }

    public static ByteBuf startGame(long uniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId, int clientProtocol) {
        CloudburstSession session = CloudburstSession.create(clientProtocol);
        return session.encode(CloudburstPackets.startGame(
                uniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId, session));
    }

    public static ByteBuf itemComponentEmpty(int clientProtocol) {
        CloudburstSession session = CloudburstSession.create(clientProtocol);
        return session.encode(CloudburstPackets.itemComponentFull(session));
    }

    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ, int clientProtocol) {
        CloudburstSession session = CloudburstSession.create(clientProtocol);
        var packet = CloudburstPackets.levelChunkEmpty(chunkX, chunkZ);
        try {
            return session.encode(packet);
        } finally {
            packet.release();
        }
    }

    public static ByteBuf encode(BedrockCodec codec, BedrockPacket packet) {
        // Codec alone is insufficient for palette-bound helpers — build a session for latest band.
        int proto = codec != null ? codec.getProtocolVersion() : CloudburstCodecIndex.MIN_MODERN;
        return CloudburstSession.create(proto).encode(packet);
    }
}
