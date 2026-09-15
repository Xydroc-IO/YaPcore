package com.yapcore.tebex;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TebexWebhookSignatureTest {

    /** Vector matching Tebex docs algorithm (SHA-256 body hex → HMAC-SHA256). */
    @Test
    void matchesKnownVector() {
        String secret = "0d45982a10e3a072d0c1261c55dd9918";
        byte[] body = "{\"id\":\"abc\",\"type\":\"validation.webhook\"}".getBytes(StandardCharsets.UTF_8);
        String sig = TebexWebhookSignature.compute(body, secret);
        assertEquals(64, sig.length());
        assertTrue(TebexWebhookSignature.verify(body, secret, sig));
        assertTrue(TebexWebhookSignature.verify(body, secret, sig.toUpperCase()));
        assertFalse(TebexWebhookSignature.verify(body, secret, "deadbeef"));
        assertFalse(TebexWebhookSignature.verify(body, "change-me", sig));
        assertFalse(TebexWebhookSignature.verify(body, secret, null));
    }

    @Test
    void rejectsTamperedBody() {
        String secret = "test-secret";
        byte[] body = "{\"type\":\"payment.completed\"}".getBytes(StandardCharsets.UTF_8);
        String sig = TebexWebhookSignature.compute(body, secret);
        byte[] tampered = "{\"type\":\"payment.refunded\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(TebexWebhookSignature.verify(tampered, secret, sig));
    }
}
