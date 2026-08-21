package com.yaplabs.yapengine.sequencing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Per-stream strict sequence barrier.
 * Out-of-order arrivals buffer in a skip-list; only the next expected
 * per-stream {@code streamSeq} is released — guaranteeing microsecond-stamped
 * player interactions never execute ahead of an earlier click/close on that lane.
 */
public final class StrictOrderedQueue<T> {

    public record Sequenced<T>(SequenceToken token, T payload) {
        public Sequenced {
            Objects.requireNonNull(token);
            Objects.requireNonNull(payload);
        }
    }

    private final String streamKey;
    private final ConcurrentSkipListMap<Long, Sequenced<T>> buffer = new ConcurrentSkipListMap<>();
    private final AtomicLong nextExpected = new AtomicLong(-1);
    private final AtomicLong released = new AtomicLong();
    private final AtomicLong buffered = new AtomicLong();

    public StrictOrderedQueue(String streamKey) {
        this.streamKey = Objects.requireNonNull(streamKey);
    }

    public String streamKey() {
        return streamKey;
    }

    /**
     * Offer a sequenced payload. Returns immediately-releasable items in order
     * (may be empty if this arrival is ahead of a gap).
     */
    public List<Sequenced<T>> offer(SequenceToken token, T payload) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(payload);
        if (!streamKey.equals(token.getStreamKey()) && !"*".equals(streamKey)) {
            throw new IllegalArgumentException("stream mismatch: " + token.getStreamKey());
        }
        long id = token.getStreamSeq();
        nextExpected.compareAndSet(-1, id);
        buffer.put(id, new Sequenced<>(token, payload));
        buffered.incrementAndGet();
        return drainReady();
    }

    public List<Sequenced<T>> drainReady() {
        List<Sequenced<T>> out = new ArrayList<>(4);
        while (true) {
            long expect = nextExpected.get();
            if (expect < 0) {
                break;
            }
            Sequenced<T> head = buffer.get(expect);
            if (head == null) {
                // Gap — wait for missing earlier token
                break;
            }
            if (!buffer.remove(expect, head)) {
                continue;
            }
            if (!nextExpected.compareAndSet(expect, expect + 1)) {
                buffer.put(expect, head);
                continue;
            }
            out.add(head);
            released.incrementAndGet();
            buffered.decrementAndGet();
        }
        return out;
    }

    public void drainTo(Consumer<Sequenced<T>> consumer) {
        for (Sequenced<T> item : drainReady()) {
            consumer.accept(item);
        }
    }

    public int bufferedCount() {
        return buffer.size();
    }

    public long releasedCount() {
        return released.get();
    }

    public long nextExpectedId() {
        return nextExpected.get();
    }
}
