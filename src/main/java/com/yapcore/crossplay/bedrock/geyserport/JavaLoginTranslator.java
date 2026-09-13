package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.codec.BedrockWorldCodec;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;

/**
 * Port of the Bedrock S2C side of Geyser {@code JavaLoginTranslator} after {@code connect()}.
 *
 * <p>Current behavior: {@code ChunkRadiusUpdated(view)} + full empty square, then wait for real
 * {@code SetLocalPlayerAsInitialized}. Publisher only after init. This is <b>not</b> a proven
 * working join — IC-90 still open; JOIN_PROBE is the source of truth.
 */
public final class JavaLoginTranslator {

    private JavaLoginTranslator() {}

    /**
     * After {@link YapGeyserSession#connect()}: advertise full view, fill empties, wait for 0x71.
     */
    public static void afterConnect(YapGeyserSession session, int javaViewDistance) {
        int view = Math.max(2, Math.min(32, javaViewDistance));
        session.setPendingFullRenderDistance(view);

        BedrockBridgeContext ctx = session.ctx();
        if (ctx.startConfigEmptyFillSent.putIfAbsent(session.guid(), Boolean.TRUE) != null) {
            return;
        }

        session.setServerRenderDistance(view);
        int sent = sendEmptyColumns(session, view);
        BedrockBridgeContext.LOG.info(
                "BE JavaLoginTranslator afterConnect (full-view-empties) user=" + session.username()
                        + " view=" + view
                        + " bedrockRadius=" + ChunkUtils.squareToCircle(view)
                        + " cols=" + sent
                        + " forceUpdate=false"
                        + " — waiting SetLocalPlayerAsInitialized (no pre-init publisher/Respawn)");
    }

    /**
     * After real 0x71: ensure full view and fill any unmarked columns.
     */
    public static int expandViewAndFillRemaining(YapGeyserSession session) {
        int full = session.pendingFullRenderDistance();
        if (full <= 0) {
            full = session.getServerRenderDistance() > 0
                    ? session.getServerRenderDistance()
                    : YapGeyserSession.DEFAULT_JAVA_VIEW;
        }
        Integer requested = session.ctx().chunkRadius.get(session.guid());
        if (requested != null) {
            full = Math.min(full, Math.max(2, requested));
        }
        if (session.getServerRenderDistance() != full) {
            session.setServerRenderDistance(full);
        }
        return fillRemainingEmptyColumns(session);
    }

    public static int fillRemainingEmptyColumns(YapGeyserSession session) {
        int view = session.getServerRenderDistance() > 0
                ? session.getServerRenderDistance()
                : YapGeyserSession.DEFAULT_JAVA_VIEW;
        Integer requested = session.ctx().chunkRadius.get(session.guid());
        if (requested != null) {
            view = Math.min(view, Math.max(2, requested));
        }
        BedrockBridgeContext ctx = session.ctx();
        Vector3i pos = session.spawnBlockPos();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        int sent = 0;
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                int nx = cx + dx;
                int nz = cz + dz;
                if (ctx.columns.wasSent(session.guid(), nx, nz)) {
                    continue;
                }
                ChunkUtils.sendEmptyChunk(session, nx, nz, false);
                ctx.columns.markSent(session.guid(), nx, nz);
                sent++;
            }
        }
        BedrockBridgeContext.LOG.info(
                "BE post-init empty fill user=" + session.username()
                        + " view=" + view + " cols=" + sent);
        return sent;
    }

    public static int sendEmptyColumns(YapGeyserSession session, int radius) {
        BedrockBridgeContext ctx = session.ctx();
        Vector3i pos = session.spawnBlockPos();
        ChunkUtils.sendEmptyChunks(session, pos, radius, false);
        int cols = (2 * radius + 1) * (2 * radius + 1);
        markSquare(ctx, session.guid(), pos.getX() >> 4, pos.getZ() >> 4, radius);
        BedrockBridgeContext.LOG.info(
                "BE sendEmptyChunks (JavaLoginTranslator) user=" + session.username()
                        + " forceUpdate=false cols=" + cols + " radius=" + radius);
        return cols;
    }

    @Deprecated
    public static int sendPaperOrEmptyColumns(YapGeyserSession session, int radius) {
        return sendEmptyColumns(session, radius);
    }

    public static LevelChunkPacket levelChunkFromHashedColumn(int chunkX, int chunkZ, int[][] states) {
        ByteBuf payload = BedrockWorldCodec.hashedColumnPayload(states);
        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setDimension(0);
        packet.setSubChunksLength(states == null ? 0 : Math.min(24, states.length));
        packet.setRequestSubChunks(false);
        packet.setCachingEnabled(false);
        packet.setData(payload);
        return packet;
    }

    private static void markSquare(BedrockBridgeContext ctx, long guid, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ctx.columns.markSent(guid, cx + dx, cz + dz);
            }
        }
    }
}
