package com.yapcore.tebex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Tebex {@code X-Signature}: SHA-256(raw body) as hex, then HMAC-SHA256(secret, bodyHashHex).
 */
public final class TebexWebhookSignature {

    private TebexWebhookSignature() {
    }

    public static String compute(byte[] rawBody, String secret) {
        if (rawBody == null) {
            rawBody = new byte[0];
        }
        if (secret == null) {
            secret = "";
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String bodyHashHex = HexFormat.of().formatHex(sha.digest(rawBody));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(bodyHashHex.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Tebex signature compute failed", e);
        }
    }

    public static boolean verify(byte[] rawBody, String secret, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        if (secret == null || secret.isBlank() || "change-me".equals(secret)) {
            return false;
        }
        String expected = compute(rawBody, secret);
        String got = providedSignature.trim().toLowerCase();
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = got.getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }
}
