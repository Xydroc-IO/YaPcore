package com.yapcore.crossplay.bedrock.cloudburst;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;

/**
 * @deprecated Prefer {@link CloudburstSession} + {@link CloudburstPackets}.
 */
@Deprecated
public final class CloudburstCodecs {

    public static final int LATEST_CODEC_PROTOCOL = 2193;

    private CloudburstCodecs() {}

    public static boolean supports(int protocol) {
        return CloudburstCodecIndex.isModern(protocol);
    }

    public static BedrockCodec codecFor(int protocol) {
        return CloudburstCodecIndex.codecFor(protocol);
    }

    public static ByteBuf encodeStartGame(long entityUniqueId, long runtimeId, String levelName,
                                          int blockX, int blockY, int blockZ, int protocol) {
        return CloudburstJoinCodec.startGame(
                entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ, null, protocol);
    }

    public static ByteBuf encodeEmptyItemComponent(int protocol) {
        return CloudburstJoinCodec.itemComponentEmpty(protocol);
    }

    public static ByteBuf encodeEmptyLevelChunk(int chunkX, int chunkZ, int protocol) {
        return CloudburstJoinCodec.levelChunkEmpty(chunkX, chunkZ, protocol);
    }
}
