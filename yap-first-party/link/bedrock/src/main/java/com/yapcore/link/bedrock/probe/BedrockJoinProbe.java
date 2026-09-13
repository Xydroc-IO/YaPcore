package com.yapcore.link.bedrock.probe;

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
import java.util.logging.Logger;

/**
 * Per-guid Bedrock join wire probe for Link — timeline under
 * {@code <linkHome>/logs/bedrock-join/} (or {@code logs/bedrock-join} when home is null).
 */
public final class BedrockJoinProbe {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static final ConcurrentHashMap<Long, Trace> TRACES = new ConcurrentHashMap<>();
    private static volatile Path logHome;

    private BedrockJoinProbe() {
    }

    /** Set Link home (or any root). Timelines go to {@code home/logs/bedrock-join/}. */
    public static void startHome(Path home) {
        logHome = home;
    }

    public static void start(long guid, String user, int protocol, String address) {
        TRACES.put(guid, new Trace(guid, user != null ? user : "?", protocol, address));
        LOG.info("BE JOIN_PROBE start user=" + user
                + " guid=" + Long.toHexString(guid)
                + " proto=" + protocol
                + " addr=" + address);
    }

    public static void noteEvent(long guid, String event) {
        Trace t = TRACES.get(guid);
        if (t != null) {
            t.add("EVT", -1, event, 0, t.lastPhase);
        }
    }

    /** Persist backend address for SUMMARY (checklist: {@code java_backend=127.0.0.1:25566}). */
    public static void noteJavaBackend(long guid, String hostPort) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        if (hostPort != null && !hostPort.isBlank()) {
            t.javaBackend = hostPort.trim();
        }
        t.add("EVT", -1, "java_backend=" + t.javaBackend, 0, t.lastPhase);
    }

    public static void notePhase(long guid, String phase) {
        Trace t = TRACES.get(guid);
        if (t != null && phase != null) {
            t.lastPhase = phase;
            t.add("EVT", -1, "phase=" + phase, 0, phase);
        }
    }

    public static void noteS2C(long guid, int packetId, String name, int encodedBytes) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        String label = name != null ? name : nameForId(packetId);
        t.add("S2C", packetId, label, encodedBytes, t.lastPhase);
        if (packetId == 0x3a || (label != null && label.contains("LevelChunk"))) {
            t.levelChunks.incrementAndGet();
            t.levelChunkBytes.addAndGet(Math.max(0, encodedBytes));
        }
    }

    /** Record JE→BE chunk translation outcome (REAL vs EMPTY_FALLBACK). */
    public static void noteLevelChunk(long guid, boolean real, int jePayloadBytes) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        if (real) {
            t.realLevelChunks.incrementAndGet();
        } else {
            t.emptyLevelChunks.incrementAndGet();
        }
        t.javaLevelChunkEvents.incrementAndGet();
        t.javaLevelChunkBytes.addAndGet(Math.max(0, jePayloadBytes));
    }

    public static void noteC2S(long guid, int packetId, int bodyBytes) {
        Trace t = TRACES.get(guid);
        if (t == null) {
            return;
        }
        t.add("C2S", packetId, nameForId(packetId), bodyBytes, t.lastPhase);
        if ((packetId & 0x3ff) == 0x71) {
            t.gotInit71 = true;
        }
        if ((packetId & 0x3ff) == 0x45) {
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
     * Write timeline + summary; remove trace.
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
            Path base = logHome != null ? logHome.resolve("logs").resolve("bedrock-join")
                    : Path.of("logs", "bedrock-join");
            Files.createDirectories(base);
            String stamp = STAMP.format(Instant.ofEpochMilli(t.startedMs));
            String safeUser = t.user.replaceAll("[^A-Za-z0-9._-]", "_");
            out = base.resolve(stamp + "-" + safeUser + "-" + Long.toHexString(guid) + ".log");
            Files.writeString(out, t.render(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.warning("BE JOIN_PROBE write fail: " + e.getMessage());
        }
        LOG.info(t.summaryLine(out));
        return out;
    }

    /**
     * Finish after {@code delayMs} so post-SPAWN auth/dig evidence is captured. Immediate
     * {@link #finish} still wins if the player disconnects first.
     */
    public static void scheduleFinish(long guid, String reason, long delayMs) {
        if (!TRACES.containsKey(guid)) {
            return;
        }
        noteEvent(guid, "probe keep-open " + Math.max(0L, delayMs) + "ms for post-SPAWN dig/auth");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0L, delayMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            if (TRACES.containsKey(guid)) {
                finish(guid, reason != null ? reason : "JOIN_OK");
            }
        }, "yap-bedrock-join-probe-" + Long.toHexString(guid));
        t.setDaemon(true);
        t.start();
    }

    private static String nameForId(int id) {
        int bare = id & 0x3ff;
        return switch (bare) {
            case 0x01 -> "LOGIN";
            case 0x03 -> "SERVER_TO_CLIENT_HANDSHAKE";
            case 0x04 -> "CLIENT_TO_SERVER_HANDSHAKE";
            case 0x06 -> "RESOURCE_PACKS_INFO";
            case 0x07 -> "RESOURCE_PACK_STACK";
            case 0x08 -> "RESOURCE_PACK_CLIENT_RESPONSE";
            case 0x02 -> "PLAY_STATUS";
            case 0xc1 -> "REQUEST_NETWORK_SETTINGS";
            case 0x8f -> "NETWORK_SETTINGS";
            case 0x71 -> "SET_LOCAL_PLAYER_AS_INITIALIZED";
            case 0x45 -> "REQUEST_CHUNK_RADIUS";
            case 0x90 -> "PLAYER_AUTH_INPUT";
            case 0x24 -> "PLAYER_ACTION";
            case 0x1e -> "INVENTORY_TRANSACTION";
            case 0x21 -> "INTERACT";
            case 0x2c -> "ANIMATE";
            case 0x4d -> "TEXT";
            case 0x4c -> "AVAILABLE_COMMANDS";
            default -> "id_0x" + Integer.toHexString(bare);
        };
    }

    private static final class Trace {
        final long guid;
        final String user;
        final int protocol;
        final String address;
        final long startedMs = System.currentTimeMillis();
        long endedMs;
        String endReason = "";
        volatile String lastPhase = "?";
        boolean gotInit71;
        boolean gotRequestRadius;
        final AtomicInteger levelChunks = new AtomicInteger();
        final AtomicLong levelChunkBytes = new AtomicLong();
        final AtomicInteger javaLevelChunkEvents = new AtomicInteger();
        final AtomicInteger realLevelChunks = new AtomicInteger();
        final AtomicInteger emptyLevelChunks = new AtomicInteger();
        final AtomicLong javaLevelChunkBytes = new AtomicLong();
        final AtomicInteger udpOutPkts = new AtomicInteger();
        final AtomicLong udpOutBytes = new AtomicLong();
        final AtomicInteger udpInPkts = new AtomicInteger();
        final AtomicLong udpInBytes = new AtomicLong();
        volatile String javaBackend = "";
        final List<String> lines = new ArrayList<>(256);

        Trace(long guid, String user, int protocol, String address) {
            this.guid = guid;
            this.user = user;
            this.protocol = protocol;
            this.address = address != null ? address : "";
            lines.add("# Bedrock JOIN_PROBE (Link) guid=" + Long.toHexString(guid)
                    + " user=" + user + " proto=" + protocol + " addr=" + address
                    + " start=" + Instant.ofEpochMilli(startedMs));
        }

        synchronized void add(String dir, int id, String name, int bytes, String phase) {
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
            sb.append("java_backend=").append(javaBackend.isEmpty() ? "?" : javaBackend).append('\n');
            sb.append("got0x71_SetLocalPlayerAsInitialized=").append(gotInit71).append('\n');
            sb.append("gotRequestChunkRadius=").append(gotRequestRadius).append('\n');
            sb.append("s2cLevelChunks=").append(levelChunks.get())
                    .append(" s2cLevelChunkBytes=").append(levelChunkBytes.get()).append('\n');
            sb.append("javaLevelChunkEvents=").append(javaLevelChunkEvents.get())
                    .append(" real=").append(realLevelChunks.get())
                    .append(" emptyFallback=").append(emptyLevelChunks.get())
                    .append(" jeBytes=").append(javaLevelChunkBytes.get()).append('\n');
            sb.append("udpOutPkts=").append(udpOutPkts.get())
                    .append(" udpOutBytes=").append(udpOutBytes.get()).append('\n');
            sb.append("udpInPkts=").append(udpInPkts.get())
                    .append(" udpInBytes=").append(udpInBytes.get()).append('\n');
            if ("AWAITING_JOIN".equals(lastPhase)) {
                sb.append("phase1Done=true (packs COMPLETED)\n");
            }
            return sb.toString();
        }

        String summaryLine(Path file) {
            return "BE JOIN_PROBE done user=" + user
                    + " guid=" + Long.toHexString(guid)
                    + " ms=" + (endedMs - startedMs)
                    + " phase=" + lastPhase
                    + " got0x71=" + gotInit71
                    + " javaChunks=" + javaLevelChunkEvents.get()
                    + " real=" + realLevelChunks.get()
                    + " empty=" + emptyLevelChunks.get()
                    + " udpOutB=" + udpOutBytes.get()
                    + " udpInB=" + udpInBytes.get()
                    + " reason=" + endReason
                    + (file != null ? " file=" + file : " file=WRITE_FAIL");
        }
    }
}
