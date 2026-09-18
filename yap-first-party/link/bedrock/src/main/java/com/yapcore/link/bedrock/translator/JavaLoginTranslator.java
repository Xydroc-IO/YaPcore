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
     * Seed a small empty disk so the Bedrock client can finish join (0x71).
     * Full JE view is advertised as pending — REAL columns from Folia replace empties;
     * do not pre-fill the entire viewDistance² (was 1681 void columns masking the world).
     */
    private static final int JOIN_SEED_EMPTY_RADIUS = 2;

    public static void afterConnect(LinkBedrockSession session, int javaViewDistance) {
        int view = Math.max(2, Math.min(32, javaViewDistance));
        session.setPendingFullRenderDistance(view);
        // Advertise full circle to the client, but only seed a tiny empty disk.
        session.setServerRenderDistance(view);
        int seed = Math.min(JOIN_SEED_EMPTY_RADIUS, view);
        int sent = sendEmptyColumns(session, seed);
        BedrockJoinProbe.noteEvent(session.guid(),
                "afterConnect view=" + view + " seedEmptyR=" + seed + " cols=" + sent + " awaiting_0x71");
        LOG.info("BE JavaLoginTranslator afterConnect user=" + session.username()
                + " view=" + view
                + " seedEmptyR=" + seed
                + " bedrockRadius=" + ChunkUtils.squareToCircle(view)
                + " cols=" + sent
                + " — waiting SetLocalPlayerAsInitialized (no pre-init publisher)");
    }

    public static int expandViewAndFillRemaining(LinkBedrockSession session) {
        int full = session.pendingFullRenderDistance();
        if (full <= 0) {
            full = session.getServerRenderDistance() > 0
                    ? session.getServerRenderDistance()
                    : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        }
        // Do NOT clamp to Bedrock RequestChunkRadius (clientRenderDistance).
        // Geyser keeps serverRenderDistance from JE Login and only forwards the client
        // radius to Java Client Information. Clamping here shrank ChunkRadiusUpdated
        // from server view (32) down to the client's slider (often 8–10) → fog wall.
        if (session.getServerRenderDistance() != full) {
            session.setServerRenderDistance(full);
        }
        return fillRemainingEmptyColumns(session);
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
