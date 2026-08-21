package com.yaplabs.yapengine.sequencing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Retention guards for months-long SequenceToken maps. */
class SequenceTokenRetentionTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SequenceToken.resetForTests();
    }

    @Test
    void forgetRemovesFromLive() {
        SequenceToken t = SequenceToken.next("player:a");
        assertEquals(1, SequenceToken.liveSize());
        t.forget();
        assertEquals(0, SequenceToken.liveSize());
    }

    @Test
    void streamKeysBoundedByForgetStream() {
        SequenceToken.next("bot-0");
        SequenceToken.next("bot-0");
        SequenceToken.next("bot-1");
        assertEquals(2, SequenceToken.streamKeyCount());
        SequenceToken.forgetStream("bot-0");
        assertEquals(1, SequenceToken.streamKeyCount());
    }

    @Test
    void pruneOlderThanClearsOrphans() throws InterruptedException {
        SequenceToken orphan = SequenceToken.next("orphan");
        assertEquals(1, SequenceToken.liveSize());
        Thread.sleep(5);
        int removed = SequenceToken.pruneOlderThan(1);
        assertTrue(removed >= 1);
        assertEquals(0, SequenceToken.liveSize());
        orphan.forget(); // no-op if already pruned
    }
}
