package com.yaplabs.yapengine.sequencing;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Multiplexes {@link StrictOrderedQueue}s by player/stream key so each
 * player's interactions stay perfectly ordered at microsecond precision
 * without blocking unrelated players.
 */
public final class InteractionSequencer<T> {

    private final ConcurrentHashMap<String, StrictOrderedQueue<T>> streams = new ConcurrentHashMap<>();

    public SequenceToken stamp(String streamKey) {
        return SequenceToken.next(streamKey);
    }

    public SequenceToken stamp(String streamKey, int channelId) {
        return SequenceToken.next(streamKey, channelId);
    }

    public void publish(SequenceToken token, T payload, Consumer<StrictOrderedQueue.Sequenced<T>> onReady) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(onReady);
        StrictOrderedQueue<T> queue = streams.computeIfAbsent(
                token.getStreamKey(), StrictOrderedQueue::new);
        List<StrictOrderedQueue.Sequenced<T>> ready = queue.offer(token, payload);
        for (StrictOrderedQueue.Sequenced<T> item : ready) {
            onReady.accept(item);
        }
    }

    public StrictOrderedQueue<T> stream(String streamKey) {
        return streams.computeIfAbsent(streamKey, StrictOrderedQueue::new);
    }

    public int activeStreams() {
        return streams.size();
    }

    public void forgetStream(String streamKey) {
        streams.remove(streamKey);
    }
}
