package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * Per-guid Bedrock join wire probe — records S2C/C2S game packets + UDP datagram sizes,
 * then dumps a timeline file on disconnect. Built to stop guessing IC-90 causes.
 *
 * <p>Output: {@code logs/bedrock-join/<stamp>-&lt;user&gt;-&lt;guid&gt;.log}
 */
public final class BedrockJoinProbe {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static final ConcurrentHashMap<Long, Trace> TRACES = new ConcurrentHashMap<>();
    private static volatile BedrockBridgeContext CTX;

    private BedrockJoinProbe() {}

    public static void bindContext(BedrockBridgeContext ctx) {
        CTX = ctx;
    }

    public static void start(long guid, String user, int protocol, String address) {
        TRACES.put(guid, new Trace(guid, user != null ? user : "?", protocol, address));
        BedrockBridgeContext.LOG.info(
                "BE JOIN_PROBE start user=" + user
                        + " guid=" + Long.toHexString(guid)
                        + " proto=" + protocol
                        + " addr=" + address);
    }

    public static void noteEvent(long guid, String event) {
        Trace t = TRACES.get(guid);
        if (t != null) {
            t.add("EVT", -1, event, 0, phaseOf(guid));
        }
    }

    public static void noteS2C(long guid, int packetId, String name, int encodedBytes) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        String label = name != null ? name : nameForId(packetId);
        t.add("S2C", packetId, label, encodedBytes, phaseOf(guid));
        if (packetId == BedrockPacketIds.LEVEL_CHUNK.id
                || (label != null && label.contains("LevelChunk"))) {
            t.levelChunks.incrementAndGet();
            t.levelChunkBytes.addAndGet(Math.max(0, encodedBytes));
        }
        if (packetId == BedrockPacketIds.CHUNK_RADIUS_UPDATED.id) {
            t.chunkRadiusUpdates.incrementAndGet();
        }
        if (packetId == BedrockPacketIds.NETWORK_CHUNK_PUBLISHER_UPDATE.id) {
            t.publishers.incrementAndGet();
        }
        if (packetId == BedrockPacketIds.START_GAME.id) {
            t.startGameSeen = true;
        }
        if (packetId == BedrockPacketIds.PLAY_STATUS.id) {
            t.playStatusSeen = true;
        }
    }

    public static void noteS2CPacket(long guid, BedrockPacket packet, int packetId, int encodedBytes) {
        if (packet == null) {
            return;
        }
        noteS2C(guid, packetId, packet.getClass().getSimpleName(), encodedBytes);
    }

    public static void noteC2S(long guid, int packetId, int bodyBytes) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        t.add("C2S", packetId, nameForId(packetId), bodyBytes, phaseOf(guid));
        if (packetId == BedrockPacketIds.SET_LOCAL_PLAYER_AS_INITIALIZED.id) {
            t.gotInit71 = true;
        }
        if (packetId == BedrockPacketIds.REQUEST_CHUNK_RADIUS.id) {
            t.gotRequestRadius = true;
        }
    }

    public static void noteUdpOut(long guid, int datagramBytes) {
        Trace t = TRACES.get(guid);
        if (t != null) {
            t.udpOutPkts.incrementAndGet();
            t.udpOutBytes.addAndGet(Math.max(0, datagramBytes));
        }
    }

    public static void noteUdpIn(long guid, int datagramBytes) {
        Trace t = TRACES.get(guid);
        if (t != null) {
            t.udpInPkts.incrementAndGet();
            t.udpInBytes.addAndGet(Math.max(0, datagramBytes));
        }
    }

    public static boolean isActive(long guid) {
        return TRACES.containsKey(guid);
    }

    /**
     * Write timeline + summary; remove trace. Safe to call multiple times.
     *
     * @return path written, or null
     */
    public static Path finish(long guid, String reason) {
        Trace t = TRACES.remove(guid);
        if (t == null) {
            return null;
        }
        t.endedMs = System.currentTimeMillis();
        t.endReason = reason != null ? reason : "unknown";
        Path out = null;
        try {
            Path dir = Path.of("logs", "bedrock-join");
            Files.createDirectories(dir);
            String stamp = STAMP.format(Instant.ofEpochMilli(t.startedMs));
            String safeUser = t.user.replaceAll("[^A-Za-z0-9._-]", "_");
            out = dir.resolve(stamp + "-" + safeUser + "-" + Long.toHexString(guid) + ".log");
            Files.writeString(out, t.render(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            BedrockBridgeContext.LOG.warning("BE JOIN_PROBE write fail: " + e.getMessage());
        }
        BedrockBridgeContext.LOG.info(t.summaryLine(out));
        return out;
    }

    private static BedrockBridgeContext.LoginPhase phaseOf(long guid) {
        BedrockBridgeContext ctx = CTX;
        if (ctx != null) {
            BedrockBridgeContext.LoginPhase p = ctx.loginPhase.get(guid);
            if (p != null) {
                return p;
            }
        }
        Trace t = TRACES.get(guid);
        return t != null ? t.lastPhase : null;
    }

    private static String nameForId(int id) {
        int bare = id & 0x3ff;
        BedrockPacketIds known = BedrockPacketIds.byId(bare);
        if (known != null) {
            return known.name() + (bare != id ? "(hdr=0x" + Integer.toHexString(id) + ")" : "");
        }
        // JOIN_PROBE aliases when enum lags Cloudburst
        if (bare == 0x138) {
            return "SERVERBOUND_LOADING_SCREEN";
        }
        if (bare == 0x141) {
            return "CLIENT_CAMERA_AIM_ASSIST";
        }
        return "id_0x" + Integer.toHexString(bare);
    }

    private static final class Trace {
        final long guid;
        final String user;
        final int protocol;
        final String address;
        final long startedMs = System.currentTimeMillis();
        long endedMs;
        String endReason = "";
        volatile BedrockBridgeContext.LoginPhase lastPhase;
        boolean startGameSeen;
        boolean playStatusSeen;
        boolean gotInit71;
        boolean gotRequestRadius;
        final AtomicInteger levelChunks = new AtomicInteger();
        final AtomicLong levelChunkBytes = new AtomicLong();
        final AtomicInteger chunkRadiusUpdates = new AtomicInteger();
        final AtomicInteger publishers = new AtomicInteger();
        final AtomicInteger udpOutPkts = new AtomicInteger();
        final AtomicLong udpOutBytes = new AtomicLong();
        final AtomicInteger udpInPkts = new AtomicInteger();
        final AtomicLong udpInBytes = new AtomicLong();
        final List<String> lines = new ArrayList<>(256);

        Trace(long guid, String user, int protocol, String address) {
            this.guid = guid;
            this.user = user;
            this.protocol = protocol;
            this.address = address != null ? address : "";
            lines.add("# Bedrock JOIN_PROBE guid=" + Long.toHexString(guid)
                    + " user=" + user + " proto=" + protocol + " addr=" + address
                    + " start=" + Instant.ofEpochMilli(startedMs));
        }

        synchronized void add(String dir, int id, String name, int bytes,
                              BedrockBridgeContext.LoginPhase phase) {
            if (phase != null) {
                lastPhase = phase;
            }
            long t = System.currentTimeMillis() - startedMs;
            String idHex = id >= 0 ? String.format(Locale.ROOT, "0x%02x", id) : "-";
            lines.add(String.format(Locale.ROOT, "+%5dms  %-3s  %-6s  %-40s  %6d B  phase=%s",
                    t, dir, idHex, name != null ? name : "?", bytes,
                    lastPhase != null ? lastPhase : "?"));
        }

        String render() {
            StringBuilder sb = new StringBuilder(Math.max(2048, lines.size() * 80));
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            sb.append('\n');
            sb.append("=== SUMMARY ===\n");
            sb.append("durationMs=").append(endedMs - startedMs).append('\n');
            sb.append("endReason=").append(endReason).append('\n');
            sb.append("lastPhase=").append(lastPhase).append('\n');
            sb.append("startGame=").append(startGameSeen)
                    .append(" playStatus=").append(playStatusSeen).append('\n');
            sb.append("got0x71_SetLocalPlayerAsInitialized=").append(gotInit71).append('\n');
            sb.append("gotRequestChunkRadius=").append(gotRequestRadius).append('\n');
            sb.append("s2cLevelChunks=").append(levelChunks.get())
                    .append(" levelChunkBytes=").append(levelChunkBytes.get()).append('\n');
            sb.append("s2cChunkRadiusUpdated=").append(chunkRadiusUpdates.get())
                    .append(" s2cPublisher=").append(publishers.get()).append('\n');
            sb.append("udpOutPkts=").append(udpOutPkts.get())
                    .append(" udpOutBytes=").append(udpOutBytes.get()).append('\n');
            sb.append("udpInPkts=").append(udpInPkts.get())
                    .append(" udpInBytes=").append(udpInBytes.get()).append('\n');
            sb.append("hypothesisHints=\n");
            if (!gotInit71) {
                sb.append("  - client never sent 0x71 (IC-90 / never-init)\n");
            }
            if (levelChunks.get() == 0) {
                sb.append("  - no LevelChunk S2C before disconnect\n");
            }
            if (chunkRadiusUpdates.get() == 0) {
                sb.append("  - no ChunkRadiusUpdated S2C\n");
            }
            if (publishers.get() > 0 && !gotInit71) {
                sb.append("  - publisher sent before 0x71 (join path expects publisher only after init)\n");
            }
            if (udpOutBytes.get() == 0 && levelChunks.get() > 0) {
                sb.append("  - game packets encoded but UDP out counter 0 (UDP hook miss?)\n");
            }
            return sb.toString();
        }

        String summaryLine(Path file) {
            return "BE JOIN_PROBE done user=" + user
                    + " guid=" + Long.toHexString(guid)
                    + " ms=" + (endedMs - startedMs)
                    + " phase=" + lastPhase
                    + " got0x71=" + gotInit71
                    + " levelChunks=" + levelChunks.get()
                    + " chunkRadiusUpd=" + chunkRadiusUpdates.get()
                    + " publisher=" + publishers.get()
                    + " udpOutB=" + udpOutBytes.get()
                    + " udpInB=" + udpInBytes.get()
                    + " reason=" + endReason
                    + (file != null ? " file=" + file : " file=WRITE_FAIL");
        }
    }
}
