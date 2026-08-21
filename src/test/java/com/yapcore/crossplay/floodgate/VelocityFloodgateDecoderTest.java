package com.yapcore.crossplay.floodgate;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityFloodgateDecoderTest {

    @Test
    void uuidMatchesRealFloodgate() {
        assertEquals(new UUID(0L, 123456789L), FloodgateAuth.uuidFromXuid("123456789"));
        assertTrue(VelocityFloodgateDecoder.isFloodgateUuid(new UUID(0L, 99L)));
        assertEquals(99L, VelocityFloodgateDecoder.xuidFromFloodgateUuid(new UUID(0L, 99L)).orElse(-1L));
    }

    @Test
    void cipherRoundTripAndBedrockParse() throws Exception {
        byte[] key = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (i + 1);
        }
        FloodgateCipher cipher = new FloodgateCipher(key);
        String plain = String.join("\0",
                "2.2.4",
                "SteveBE",
                "2535472788871234",
                "7",
                "en_US",
                "0",
                "1",
                "1.2.3.4",
                "null",
                "1",
                "sub",
                "code");
        byte[] enc = cipher.encrypt(plain.getBytes(StandardCharsets.UTF_8));
        String encStr = new String(enc, StandardCharsets.UTF_8);
        assertTrue(encStr.startsWith(FloodgateCipher.IDENTIFIER));

        String host = "lobby.example.com\0" + encStr;
        VelocityFloodgateDecoder.HostnameSplit split = VelocityFloodgateDecoder.separateHostname(host);
        assertEquals(encStr, split.floodgatePayload());
        assertEquals("lobby.example.com", split.cleanHostname());

        FloodgateBedrockData data = new VelocityFloodgateDecoder(cipher)
                .decryptPayload(encStr)
                .orElseThrow();
        assertEquals("SteveBE", data.username());
        assertEquals(2535472788871234L, data.xuid());
        assertEquals(new UUID(0L, 2535472788871234L), data.floodgateJavaUuid());
        assertTrue(data.linkedAccount().isEmpty());
    }

    @Test
    void linkedPlayerParsed() {
        var linked = FloodgateBedrockData.LinkedJavaAccount.parse(
                "JavaSteve;11111111-1111-1111-1111-111111111111;00000000-0000-0000-0000-000000000001");
        assertTrue(linked.isPresent());
        assertEquals("JavaSteve", linked.get().javaUsername());
    }
}
