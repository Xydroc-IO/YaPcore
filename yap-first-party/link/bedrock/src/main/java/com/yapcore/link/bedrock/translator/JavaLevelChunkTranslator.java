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

        // 015924 path: send REAL as they arrive during AWAITING_CLIENT_INIT (do not buffer).
        sendTranslated(session, chunkX, chunkZ, jePayload);
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
        try {
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
            maybeLogRealHex(session, real);
            session.sendUpstreamPacket(real);
            if (!refresh) {
                session.markColumnSent(chunkX, chunkZ);
            }
            session.noteRealJeChunkSent();
            session.noteSpawnColumnReal(chunkX, chunkZ);
            session.flushPendingSpawnSolidSample();
            BedrockJoinProbe.noteLevelChunk(session.guid(), true, payloadBytes);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_level_chunk→bedrock REAL dim=" + session.bedrockDimensionId()
                            + " cx=" + chunkX + " cz=" + chunkZ
                            + " jeBytes=" + payloadBytes
                            + (refresh ? " refresh" : ""));
            // Rate-limit console: first 3 REAL only (probe already has full timeline).
            long n = session.realJeChunksSent();
            if (n <= 3L) {
                LOG.info("BE JavaLevelChunk→Bedrock REAL user=" + session.username()
                        + " cx=" + chunkX + " cz=" + chunkZ
                        + " jeBytes=" + payloadBytes
                        + " #" + n);
            }
            return;
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

    /** After real 0x71: force-update spawn column when no REAL was sent yet. */
    public static void forceSpawnColumnRefresh(LinkBedrockSession session) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        if (session.realJeChunksSent() > 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "spawn_column skip_forceUpdate reals=" + session.realJeChunksSent());
            return;
        }
        int cx = session.spawnX() >> 4;
        int cz = session.spawnZ() >> 4;
        ChunkUtils.sendEmptyChunk(session, cx, cz, true);
        session.markColumnSent(cx, cz);
        BedrockJoinProbe.noteEvent(session.guid(),
                "spawn_column forceUpdate empty+stone cx=" + cx + " cz=" + cz);
        LOG.info("BE spawn column forceUpdate (Geyser dim-switch style) user="
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
