package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;

/**
 * Port of Geyser {@code BedrockRequestChunkRadiusTranslator}.
 *
 * <p>Stores client render distance only. Does <b>not</b> send ChunkRadiusUpdated or LevelChunks
 * (Geyser forwards settings to Java; YaP has no Java client-settings channel on this path).
 */
public final class BedrockRequestChunkRadiusTranslator {

    private BedrockRequestChunkRadiusTranslator() {}

    public static void translate(YapGeyserSession session, int requestedRadius) {
        int requested = Math.max(2, Math.min(32, requestedRadius));
        session.setClientRenderDistance(requested);
        BedrockBridgeContext.LoginPhase phase = session.ctx().loginPhase.get(session.guid());
        int stay = Math.min(requested, YapGeyserSession.DEFAULT_JAVA_VIEW);
        session.ctx().columns.setRadius(session.guid(), stay);
        BedrockBridgeContext.LOG.info(
                "BE REQUEST_CHUNK_RADIUS store-only (BedrockRequestChunkRadiusTranslator) "
                        + session.username()
                        + " requested=" + requested + " stay=" + stay
                        + " phase=" + phase);
    }
}
