package com.yapcore.crossplay.bedrock.cloudburst;

import java.util.Comparator;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;

/** Protocol → Cloudburst codec + palette band selection. */
public final class CloudburstCodecIndex {

    public static final int MIN_MODERN = 2168;

    private static final List<BedrockCodec> CODECS = List.of(
            Bedrock_v2193.CODEC,
            Bedrock_v2169.CODEC,
            Bedrock_v2168.CODEC
    );

    private CloudburstCodecIndex() {}

    /** Highest codec with protocolVersion &lt;= clientProto; if client is newer, use latest. */
    public static BedrockCodec codecFor(int clientProtocol) {
        return CODECS.stream()
                .filter(c -> c.getProtocolVersion() <= clientProtocol)
                .max(Comparator.comparingInt(BedrockCodec::getProtocolVersion))
                .orElse(CODECS.get(0));
    }

    /** Palette resource band for a selected codec protocol version. */
    public static String bandFor(int codecProtocol) {
        return codecProtocol >= 2193 ? "band_26_50" : "band_26_40";
    }

    public static boolean isModern(int clientProtocol) {
        return clientProtocol >= MIN_MODERN;
    }
}
