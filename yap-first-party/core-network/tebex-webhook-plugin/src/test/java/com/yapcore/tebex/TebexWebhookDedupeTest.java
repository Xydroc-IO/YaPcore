package com.yapcore.tebex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TebexWebhookDedupeTest {

    @TempDir
    Path temp;

    @Test
    void rejectsSecondDelivery() throws Exception {
        TebexWebhookDedupe dedupe = new TebexWebhookDedupe(temp, Logger.getGlobal());
        dedupe.open(30);
        assertTrue(dedupe.tryMark("webhook", "wh-1"));
        assertFalse(dedupe.tryMark("webhook", "wh-1"));
        assertTrue(dedupe.tryMark("tx", "tbx-1"));
        assertFalse(dedupe.tryMark("tx", "tbx-1"));
        assertTrue(dedupe.tryMark("webhook", "wh-2"));
        dedupe.close();
    }
}
