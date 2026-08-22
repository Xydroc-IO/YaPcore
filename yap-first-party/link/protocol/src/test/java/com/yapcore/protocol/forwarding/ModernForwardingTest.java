package com.yapcore.protocol.forwarding;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModernForwardingTest {

    @Test
    void signedPayloadHasHmacPrefix() throws Exception {
        byte[] secret = "test-secret-value".getBytes(StandardCharsets.UTF_8);
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        ByteBuf buf = ModernForwarding.createForwardingData(
                secret, "10.0.0.5", id, "Steve", List.of());
        try {
            assertTrue(buf.readableBytes() > 32);
            byte[] sig = new byte[32];
            buf.readBytes(sig);
            byte[] body = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), body);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            assertArrayEquals(mac.doFinal(body), sig);
            assertEquals(ModernForwarding.MODERN_DEFAULT, body[0]);
        } finally {
            buf.release();
        }
    }
}
