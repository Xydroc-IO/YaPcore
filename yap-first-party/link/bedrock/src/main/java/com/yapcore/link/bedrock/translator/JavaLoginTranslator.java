package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Bedrock S2C after {@code connect()}: advertise view + empty square; wait real 0x71.
 * No soft-init; publisher only after {@link BedrockSetLocalPlayerAsInitializedTranslator}.
 */
public final class JavaLoginTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaLoginTranslator() {
    }

    /**
     * Square chunk radius requested from Folia and advertised to Bedrock until 0x71.
     * View 5 still let Folia deliver ~213 REAL columns (~4.6MB) before init; mobile
     * then sat ~52s decoding/receiving before 0x71 (095401), so AddPlayer stayed
     * buffered and Bedrock saw no Java players. Radius 2 ≈ 5×5; outer REAL columns
     * are buffered and flushed after 0x71 (not dropped — Folia will not resend).
     */
    public static final int JOIN_BEDROCK_VIEW = 2;

    /**
     * Soft-SPAWN (pre-0x71) opens fog to the client slider, capped here so we do not
     * flood ~1000 LevelChunks while the loading screen is still up. Real 0x71 then
     * expands to {@link LinkBedrockSession#pendingFullRenderDistance()}.
     */
    public static final int SOFT_PLAYABLE_VIEW_MIN = 8;
    public static final int SOFT_PLAYABLE_VIEW_MAX = 12;

    /** Floor after real 0x71 — join radius 2 felt like a fog wall next to JE view 32. */
    public static final int MIN_POST_INIT_VIEW = 16;

    public static void afterConnect(LinkBedrockSession session, int javaViewDistance) {
        int full = Math.max(2, Math.min(32, javaViewDistance));
        session.setPendingFullRenderDistance(full);
        int joinView = Math.min(JOIN_BEDROCK_VIEW, full);
        session.setServerRenderDistance(joinView);
        // Do not seed SubChunksLength=0 columns. Bedrock keeps that empty column when
        // the real one arrives second, which blanks the spawn the player is standing on.
        // Outer REAL beyond joinView is deferred (see JavaLevelChunkTranslator) until 0x71.
        BedrockJoinProbe.noteEvent(session.guid(),
                "afterConnect joinView=" + joinView + " fullView=" + full
                        + " seedEmptyR=0 cols=0 deferOuter=true awaiting_0x71");
        LOG.info("BE JavaLoginTranslator afterConnect user=" + session.username()
                + " joinView=" + joinView
                + " fullView=" + full
                + " bedrockRadius=" + joinView
                + " — waiting SetLocalPlayerAsInitialized (no empty seed)");
    }

    public static int expandViewAndFillRemaining(LinkBedrockSession session) {
        int full = session.pendingFullRenderDistance();
        if (full <= 0) {
            full = session.getServerRenderDistance() > 0
                    ? session.getServerRenderDistance()
                    : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        }
        full = Math.max(MIN_POST_INIT_VIEW, Math.min(32, full));
        // Do NOT clamp to Bedrock RequestChunkRadius (clientRenderDistance).
        // Geyser keeps serverRenderDistance from JE Login and only forwards the client
        // radius to Java Client Information. Clamping here shrank ChunkRadiusUpdated
        // from server view (32) down to the client's slider (often 8–10) → fog wall.
        if (session.getServerRenderDistance() != full) {
            session.setServerRenderDistance(full);
        }
        return fillRemainingEmptyColumns(session);
    }

    /**
     * Soft-SPAWN mid-expand: open fog + Folia streaming to the client slider (8–12)
     * without waiting for real 0x71. Full JE view still applied in
     * {@link BedrockSetLocalPlayerAsInitializedTranslator#expandPostInitView}.
     */
    public static int expandSoftPlayableView(LinkBedrockSession session) {
        if (session == null || !session.markSoftPlayableViewExpanded()) {
            return session != null ? session.getServerRenderDistance() : 0;
        }
        int client = session.getClientRenderDistance();
        int soft = client > 0 ? client : SOFT_PLAYABLE_VIEW_MIN;
        soft = Math.max(SOFT_PLAYABLE_VIEW_MIN, Math.min(SOFT_PLAYABLE_VIEW_MAX, soft));
        int full = session.pendingFullRenderDistance();
        if (full > 0) {
            soft = Math.min(soft, full);
        }
        session.setServerRenderDistance(soft);
        var down = session.downstream();
        if (down != null) {
            down.sendClientInformationView(soft);
        }
        Vector3i pos = session.spawnBlockPos();
        if (pos != null) {
            ChunkUtils.forceUpdateChunkPosition(session, pos);
        }
        JavaLevelChunkTranslator.flushBufferedRealChunks(session);
        BedrockJoinProbe.noteEvent(session.guid(),
                "soft_playable_view r=" + soft + " client=" + client + " full=" + full);
        LOG.info("BE soft playable view user=" + session.username()
                + " r=" + soft + " client=" + client);
        return soft;
    }

    public static int fillRemainingEmptyColumns(LinkBedrockSession session) {
        // When Folia already delivered REAL columns, do not paint a void ring over the
        // rest of the view — JE will keep streaming. Empty fill made joins look "partial".
        if (session.realJeChunksSent() > 0 || JavaLevelChunkTranslator.realLevelChunksEnabled()) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "fillRemaining SKIP reals=" + session.realJeChunksSent()
                            + " realEnabled=" + JavaLevelChunkTranslator.realLevelChunksEnabled());
            return 0;
        }
        int view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        Vector3i pos = session.spawnBlockPos();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        int sent = 0;
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                int nx = cx + dx;
                int nz = cz + dz;
                if (session.wasColumnSent(nx, nz)) {
                    continue;
                }
                ChunkUtils.sendEmptyChunk(session, nx, nz);
                session.markColumnSent(nx, nz);
                sent++;
            }
        }
        return sent;
    }

    public static int sendEmptyColumns(LinkBedrockSession session, int radius) {
        Vector3i pos = session.spawnBlockPos();
        ChunkUtils.sendEmptyChunks(session, pos, radius);
        int cols = (2 * radius + 1) * (2 * radius + 1);
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                session.markColumnSent(cx + dx, cz + dz);
            }
        }
        return cols;
    }
}
