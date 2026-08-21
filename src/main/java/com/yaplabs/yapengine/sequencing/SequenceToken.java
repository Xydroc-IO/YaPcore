package com.yaplabs.yapengine.sequencing;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Interaction sequence token with microsecond ingest precision.
 * <ul>
 *   <li>{@code globalId} — process-wide unique id (tracing / logs)</li>
 *   <li>{@code streamSeq} — per-{@code streamKey} strictly increasing order key</li>
 *   <li>{@code ingestMicros} — monotonic µs from {@link SequenceClock}</li>
 * </ul>
 * {@link StrictOrderedQueue} orders on {@code streamSeq} so interleaved players
 * never block each other.
 */
public final class SequenceToken implements Comparable<SequenceToken> {

    private static final AtomicLong GLOBAL = new AtomicLong();
    private static final ConcurrentHashMap<String, AtomicLong> STREAM_SEQ = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, SequenceToken> LIVE = new ConcurrentHashMap<>();

    private final long globalId;
    private final long streamSeq;
    private final long ingestMicros;
    private final String streamKey;
    private final int channelId;

    private SequenceToken(long globalId, long streamSeq, long ingestMicros,
                          String streamKey, int channelId) {
        this.globalId = globalId;
        this.streamSeq = streamSeq;
        this.ingestMicros = ingestMicros;
        this.streamKey = Objects.requireNonNull(streamKey, "streamKey");
        this.channelId = channelId;
    }

    public static SequenceToken next(String streamKey) {
        return next(streamKey, 0);
    }

    public static SequenceToken next(String streamKey, int channelId) {
        Objects.requireNonNull(streamKey, "streamKey");
        long stream = STREAM_SEQ
                .computeIfAbsent(streamKey, k -> new AtomicLong())
                .incrementAndGet();
        long global = GLOBAL.incrementAndGet();
        long micros = SequenceClock.get().nextMicros();
        SequenceToken token = new SequenceToken(global, stream, micros, streamKey, channelId);
        LIVE.put(global, token);
        return token;
    }

    /** Process-wide unique id. */
    public long getGlobalId() {
        return globalId;
    }

    /**
     * Per-stream order key used by {@link StrictOrderedQueue}.
     * Also exposed as {@link #getSequenceId()} for legacy call sites.
     */
    public long getStreamSeq() {
        return streamSeq;
    }

    /** @return per-stream sequence (ordering key), not the global id */
    public long getSequenceId() {
        return streamSeq;
    }

    /** Monotonic ingest time in microseconds. */
    public long getIngestMicros() {
        return ingestMicros;
    }

    /** @deprecated use {@link #getIngestMicros()} */
    @Deprecated
    public long getTimestampNanos() {
        return ingestMicros * 1_000L;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public int getChannelId() {
        return channelId;
    }

    public long ageMicros() {
        return SequenceClock.get().nowMicros() - ingestMicros;
    }

    public static SequenceToken lookup(long globalId) {
        return LIVE.get(globalId);
    }

    public static Long lookupTimestamp(long globalId) {
        SequenceToken t = LIVE.get(globalId);
        return t == null ? null : t.ingestMicros;
    }

    public static void forget(long globalId) {
        LIVE.remove(globalId);
    }

    public void forget() {
        LIVE.remove(globalId);
    }

    /** Tokens still held in the process-wide LIVE map (should drain after idle). */
    public static int liveSize() {
        return LIVE.size();
    }

    /** Distinct stream keys that have ever been sequenced (bounded by players/sources). */
    public static int streamKeyCount() {
        return STREAM_SEQ.size();
    }

    /** Drop the per-stream counter when a player/source disconnects. */
    public static void forgetStream(String streamKey) {
        if (streamKey != null) {
            STREAM_SEQ.remove(streamKey);
        }
    }

    /**
     * Evict LIVE entries older than {@code maxAgeMicros} (orphan tokens whose
     * owners forgot to call {@link #forget()}). Returns number removed.
     */
    public static int pruneOlderThan(long maxAgeMicros) {
        long now = SequenceClock.get().nowMicros();
        int removed = 0;
        for (var e : LIVE.entrySet()) {
            SequenceToken t = e.getValue();
            if (t != null && (now - t.ingestMicros) > maxAgeMicros) {
                if (LIVE.remove(e.getKey(), t)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Test / harness only — clear process-wide tables. */
    public static void resetForTests() {
        LIVE.clear();
        STREAM_SEQ.clear();
        GLOBAL.set(0);
    }

    @Override
    public int compareTo(SequenceToken other) {
        int byStream = this.streamKey.compareTo(other.streamKey);
        if (byStream != 0) {
            return byStream;
        }
        int bySeq = Long.compare(this.streamSeq, other.streamSeq);
        if (bySeq != 0) {
            return bySeq;
        }
        return Long.compare(this.ingestMicros, other.ingestMicros);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SequenceToken that)) {
            return false;
        }
        return globalId == that.globalId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(globalId);
    }

    @Override
    public String toString() {
        return "SequenceToken{g=" + globalId
                + ", s=" + streamSeq
                + ", µs=" + ingestMicros
                + ", stream=" + streamKey
                + ", ch=" + channelId + "}";
    }
}
