package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.ByteBuf;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;

/**
 * Java LevelChunk → Bedrock LevelChunk.
 *
 * <p>Decodes JE {@code level_chunk_with_light} (776), maps block states via {@link JeToBedrockBlockMapper},
 * encodes non-empty Bedrock columns. Falls back to EMPTY_CHUNK when decode/remap/validation fails
 * or when {@code yap.link.bedrock.realLevelChunks=false}.
 *
 * <p>REAL columns are sent as Folia delivers them (015924: REAL=282 → 0x71 in 4.7s).
 * Buffering until SPAWNED (020914) left only EMPTY_PRE_SPAWN and never reached 0x71.
 */
public final class JavaLevelChunkTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /**
     * When false, never send remapped REAL columns — EMPTY only.
     * Default <b>true</b> after live joins (015412/015924) proved REAL + 0x71.
     * Kill-switch: {@code -Dyap.link.bedrock.realLevelChunks=false}.
     */
    private static final boolean REAL_LEVEL_CHUNKS = Boolean.parseBoolean(
            System.getProperty("yap.link.bedrock.realLevelChunks", "true"));

    /** One hex dump of the first REAL payload per JVM (forensics). */
    private static final AtomicBoolean LOGGED_REAL_HEX = new AtomicBoolean(false);
    /** Cap EMPTY_FALLBACK console warnings per JVM. */
    private static final AtomicInteger EMPTY_FALLBACK_LOGS = new AtomicInteger();

    private JavaLevelChunkTranslator() {
    }

    public static boolean realLevelChunksEnabled() {
        return REAL_LEVEL_CHUNKS;
    }

    public static void translate(LinkBedrockSession session, int chunkX, int chunkZ, ByteBuf jePayload) {
        if (session == null) {
            if (jePayload != null) {
                jePayload.release();
            }
            return;
        }
        if (!session.isSentSpawnPacket()) {
            // Caller should prefer session.bufferOrTranslateLevelChunk — keep safe drop here.
            if (jePayload != null) {
                jePayload.release();
            }
            return;
        }

        // During AWAITING_CLIENT_INIT, only send the join square. Outer REAL columns
        // are buffered (not dropped) and flushed after 0x71 — Folia will not resend
        // them, and flooding 200+ heavy LevelChunks before init stalls mobile 0x71.
        if (session.joinPhase() == LinkBedrockSession.JoinPhase.AWAITING_CLIENT_INIT
                && outsideJoinSquare(session, chunkX, chunkZ)) {
            session.bufferPendingRealChunk(chunkX, chunkZ, jePayload);
            return;
        }
        sendTranslated(session, chunkX, chunkZ, jePayload);
    }

    /** Chebyshev distance from spawn chunk vs {@link JavaLoginTranslator#JOIN_BEDROCK_VIEW}. */
    static boolean outsideJoinSquare(LinkBedrockSession session, int chunkX, int chunkZ) {
        return chebyshevFromSpawn(session, chunkX, chunkZ) > joinRadius(session);
    }

    static int joinRadius(LinkBedrockSession session) {
        return Math.max(2, Math.min(
                JavaLoginTranslator.JOIN_BEDROCK_VIEW,
                session.getServerRenderDistance() > 0
                        ? session.getServerRenderDistance()
                        : JavaLoginTranslator.JOIN_BEDROCK_VIEW));
    }

    static int chebyshevFromSpawn(LinkBedrockSession session, int chunkX, int chunkZ) {
        int scx = session.spawnX() >> 4;
        int scz = session.spawnZ() >> 4;
        return Math.max(Math.abs(chunkX - scx), Math.abs(chunkZ - scz));
    }

    /**
     * Once every column in the advertised join square is on the wire, re-emit
     * ChunkRadiusUpdated + NetworkChunkPublisherUpdate so Bedrock stops waiting
     * for a larger circle (095401: square filled ~4s, 0x71 only at ~56s).
     */
    static void maybeNudgeJoinSquareFilled(LinkBedrockSession session) {
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.AWAITING_CLIENT_INIT) {
            return;
        }
        int join = joinRadius(session);
        int scx = session.spawnX() >> 4;
        int scz = session.spawnZ() >> 4;
        int need = (2 * join + 1) * (2 * join + 1);
        int have = 0;
        for (int dx = -join; dx <= join; dx++) {
            for (int dz = -join; dz <= join; dz++) {
                if (session.wasRealColumnSent(scx + dx, scz + dz)) {
                    have++;
                }
            }
        }
        if (have < need) {
            return;
        }
        if (!session.markJoinSquareNudgeSent()) {
            return;
        }
        session.setServerRenderDistance(join);
        ChunkUtils.forceUpdateChunkPosition(session, session.spawnBlockPos());
        // Re-arm PLAYER_SPAWN now that the advertised square is on the wire. Sending it
        // after the first spawn-column solid (before the ring filled) left mobile clients
        // on "Generating world" until a ~45s timeout (100346: fill@2s, 0x71@47s).
        session.rearmPlayerSpawnAfterJoinSquare();
        session.scheduleJoinInitAssist();
        BedrockJoinProbe.noteEvent(session.guid(),
                "join_square_filled nudge r=" + join + " cols=" + have + "/" + need);
        LOG.info("BE join square filled nudge user=" + session.username()
                + " r=" + join + " cols=" + have);
    }

    /**
     * No-op drain for any leftover pre-0x71 buffer (legacy); REAL is no longer gated on SPAWNED.
     */
    public static void flushBufferedRealChunks(LinkBedrockSession session) {
        if (session == null || !REAL_LEVEL_CHUNKS) {
            return;
        }
        int n = session.drainPendingRealChunks((cx, cz, payload) ->
                sendTranslated(session, cx, cz, payload));
        if (n > 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_level_chunk FLUSH_REAL_POST_0x71 n=" + n);
            LOG.info("BE flush REAL LevelChunks post-0x71 user=" + session.username() + " n=" + n);
        }
    }

    private static void sendTranslated(LinkBedrockSession session, int chunkX, int chunkZ,
                                       ByteBuf jePayload) {
        boolean refresh = session.wasColumnSent(chunkX, chunkZ);
        int payloadBytes = jePayload != null ? jePayload.readableBytes() : 0;
        LevelChunkPacket real = null;
        // Retain a sign slice before release — calling sendChunkSigns on a freed buf aborts
        // noteRealJeChunkSent / spawnSolid and leaves reals=0 → empty forceUpdate hole.
        ByteBuf signSource = null;
        try {
            if (jePayload != null && jePayload.isReadable()) {
                signSource = jePayload.retainedDuplicate();
            }
            if (REAL_LEVEL_CHUNKS) {
                real = tryEncodeRealColumn(session, chunkX, chunkZ, jePayload);
            } else if (jePayload != null) {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "java_level_chunk REAL_GATED_OFF cx=" + chunkX + " cz=" + chunkZ);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "BE LevelChunk REAL encode fail user=" + session.username()
                    + " cx=" + chunkX + " cz=" + chunkZ + ": " + e.getMessage(), e);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_level_chunk REAL_ENCODE_FAIL " + e.getMessage());
            real = null;
        } finally {
            if (jePayload != null) {
                jePayload.release();
            }
        }
        if (real != null) {
            // Bookkeeping MUST complete even if sign translate throws — otherwise reals=0
            // and forceSpawnColumnRefresh blanks the spawn column into a void hole.
            try {
                maybeLogRealHex(session, real);
                session.sendUpstreamPacket(real);
                if (!refresh) {
                    session.markColumnSent(chunkX, chunkZ);
                }
                session.markRealColumnSent(chunkX, chunkZ);
                session.noteRealJeChunkSent();
                session.noteSpawnColumnReal(chunkX, chunkZ);
                session.flushPendingSpawnSolidSample();
                BedrockJoinProbe.noteLevelChunk(session.guid(), true, payloadBytes);
                BedrockJoinProbe.noteEvent(session.guid(),
                        "java_level_chunk→bedrock REAL dim=" + session.bedrockDimensionId()
                                + " cx=" + chunkX + " cz=" + chunkZ
                                + " jeBytes=" + payloadBytes
                                + (refresh ? " refresh" : ""));
                long n = session.realJeChunksSent();
                if (n <= 3L) {
                    LOG.info("BE JavaLevelChunk→Bedrock REAL user=" + session.username()
                            + " cx=" + chunkX + " cz=" + chunkZ
                            + " jeBytes=" + payloadBytes
                            + " #" + n);
                }
                maybeNudgeJoinSquareFilled(session);
            } finally {
                try {
                    if (signSource != null) {
                        JavaSignTranslator.sendChunkSigns(session, chunkX, chunkZ, signSource);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "BE sign text after REAL cx=" + chunkX + " cz=" + chunkZ, e);
                } finally {
                    if (signSource != null) {
                        signSource.release();
                        signSource = null;
                    }
                }
            }
            return;
        }
        if (signSource != null) {
            signSource.release();
        }
        ChunkUtils.sendEmptyChunk(session, chunkX, chunkZ, false);
        if (!refresh) {
            session.markColumnSent(chunkX, chunkZ);
        }
        BedrockJoinProbe.noteLevelChunk(session.guid(), false, payloadBytes);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_level_chunk→bedrock EMPTY_FALLBACK dim=" + session.bedrockDimensionId()
                        + " cx=" + chunkX + " cz=" + chunkZ
                        + " jeBytes=" + payloadBytes
                        + (!REAL_LEVEL_CHUNKS ? " gated" : "")
                        + (refresh ? " refresh" : ""));
        // Rate-limit: first 5 EMPTY_FALLBACK warnings only (avoid console flood).
        if (EMPTY_FALLBACK_LOGS.incrementAndGet() <= 5) {
            LOG.warning("BE JavaLevelChunk→Bedrock EMPTY_FALLBACK user=" + session.username()
                    + " cx=" + chunkX + " cz=" + chunkZ
                    + " jeBytes=" + payloadBytes
                    + (!REAL_LEVEL_CHUNKS ? " gated" : ""));
        } else {
            LOG.fine("BE JavaLevelChunk→Bedrock EMPTY_FALLBACK user=" + session.username()
                    + " cx=" + chunkX + " cz=" + chunkZ);
        }
    }

    private static void maybeLogRealHex(LinkBedrockSession session, LevelChunkPacket real) {
        if (!LOGGED_REAL_HEX.compareAndSet(false, true)) {
            return;
        }
        ByteBuf data = real.getData();
        String hex = LinkBedrockChunkEncoder.hexPrefix(data, 32);
        String line = "BE REAL LevelChunk hex32=" + hex
                + " subChunks=" + real.getSubChunksLength()
                + " dim=" + real.getDimension()
                + " caching=" + real.isCachingEnabled()
                + " bytes=" + (data != null ? data.readableBytes() : -1)
                + " user=" + session.username();
        LOG.info(line);
        BedrockJoinProbe.noteEvent(session.guid(), line);
    }

    /**
     * After real 0x71: never blank the spawn column with EMPTY ({@code SubChunksLength=0}).
     * That is the Bedrock "giant hole / different world" failure mode — REAL terrain already
     * on the wire gets wiped, the client falls through, and JE rubberbands.
     *
     * <p>If we truly have no column yet, place a 1-block stone UpdateBlock under feet only.
     */
    public static void forceSpawnColumnRefresh(LinkBedrockSession session) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        int cx = session.spawnX() >> 4;
        int cz = session.spawnZ() >> 4;
        if (session.realJeChunksSent() > 0
                || session.isSpawnColumnReal()
                || session.wasRealColumnSent(cx, cz)
                || session.wasColumnSent(cx, cz)) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "spawn_column skip_forceUpdate reals=" + session.realJeChunksSent()
                            + " spawnReal=" + session.isSpawnColumnReal()
                            + " realCol=" + session.wasRealColumnSent(cx, cz)
                            + " sent=" + session.wasColumnSent(cx, cz)
                            + " cx=" + cx + " cz=" + cz);
            return;
        }
        // No LevelChunk at all for spawn — stone under feet only, never EMPTY column.
        session.placeStandOnCollisionPlatform();
        BedrockJoinProbe.noteEvent(session.guid(),
                "spawn_column stone_only (no EMPTY forceUpdate) cx=" + cx + " cz=" + cz);
        LOG.info("BE spawn column stone_only (skip EMPTY forceUpdate) user="
                + session.username() + " cx=" + cx + " cz=" + cz);
    }

    private static LevelChunkPacket tryEncodeRealColumn(
            LinkBedrockSession session, int chunkX, int chunkZ, ByteBuf jePayload) {
        if (jePayload == null || !jePayload.isReadable()) {
            return null;
        }
        ByteBuf dup = jePayload.duplicate();
        int[][] jeSections = JavaChunkDecoder776.decodeColumn(dup);
        if (jeSections == null) {
            return null;
        }
        JavaDownstreamClient down = session.downstream();
        JeToBedrockBlockMapper mapper = session.blockMapper();
        if (mapper == null && down != null) {
            mapper = new JeToBedrockBlockMapper(session.protocolVersion(), down.blockRegistry());
            session.setBlockMapper(mapper);
        }
        if (mapper == null) {
            mapper = new JeToBedrockBlockMapper(session.protocolVersion(), null);
            session.setBlockMapper(mapper);
        }
        int[][] runtimeSections = new int[jeSections.length][];
        for (int i = 0; i < jeSections.length; i++) {
            runtimeSections[i] = mapper.mapSection(jeSections[i]);
        }
        LevelChunkPacket packet = LinkBedrockChunkEncoder.encodeColumn(session, chunkX, chunkZ, runtimeSections);
        if (packet == null) {
            return null;
        }
        // Defer solid/PLAYER_SPAWN until after the LevelChunk is on the wire (sendTranslated).
        int scx = session.spawnX() >> 4;
        int scz = session.spawnZ() >> 4;
        if (chunkX == scx && chunkZ == scz) {
            int airRt = mapper.airRuntimeId();
            int near = LinkBedrockChunkEncoder.countNonAirNearFeet(
                    runtimeSections,
                    LinkBedrockSession.OVERWORLD_MIN_Y,
                    session.spawnX(),
                    session.spawnFeetY(),
                    session.spawnZ(),
                    airRt);
            int column = LinkBedrockChunkEncoder.countNonAirColumn(runtimeSections, airRt);
            int uniqueRt = JeToBedrockBlockMapper.countUniqueNonAirRuntimes(runtimeSections, airRt);
            double standOn = LinkBedrockChunkEncoder.resolveStandOnFeetY(
                    runtimeSections,
                    LinkBedrockSession.OVERWORLD_MIN_Y,
                    session.spawnX(),
                    session.spawnFeetY(),
                    session.spawnZ(),
                    airRt);
            int feetBy = (int) Math.floor(session.spawnFeetY());
            boolean feetSolid = LinkBedrockChunkEncoder.isNonAirAt(
                    runtimeSections, LinkBedrockSession.OVERWORLD_MIN_Y,
                    session.spawnX(), feetBy, session.spawnZ(), airRt);
            boolean headSolid = LinkBedrockChunkEncoder.isNonAirAt(
                    runtimeSections, LinkBedrockSession.OVERWORLD_MIN_Y,
                    session.spawnX(), feetBy + 1, session.spawnZ(), airRt);
            boolean ceil2 = LinkBedrockChunkEncoder.isNonAirAt(
                    runtimeSections, LinkBedrockSession.OVERWORLD_MIN_Y,
                    session.spawnX(), feetBy + 2, session.spawnZ(), airRt);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "stand_on resolve feetY=" + String.format(java.util.Locale.ROOT, "%.3f", session.spawnFeetY())
                            + " surfaceY=" + String.format(java.util.Locale.ROOT, "%.3f", standOn)
                            + " feetSolid=" + feetSolid
                            + " headSolid=" + headSolid
                            + " ceil+2=" + ceil2
                            + " airRt=" + airRt
                            + " stoneRt=" + mapper.stoneRuntimeId()
                            + " uniqueRt=" + uniqueRt);
            // Stash counts + stand-on — applied after LevelChunk is sent (sendTranslated).
            session.pendingSpawnSolidSample(
                    near, column, mapper.mapHits(), mapper.mapMisses(), standOn);
        }
        return packet;
    }
}
