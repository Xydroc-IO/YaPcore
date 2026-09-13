package com.yapcore.crossplay.bedrock.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Geyser/Cloudburst login encryption parity checks. */
class BedrockEncryptionTest {

    @Test
    void ecdhSecretAndHandshakeJwtRoundTrip() throws Exception {
        KeyPair server = BedrockEncryption.createKeyPair();
        KeyPair client = BedrockEncryption.createKeyPair();
        byte[] token = BedrockEncryption.generateRandomToken();
        assertEquals(16, token.length);

        String jwt = BedrockEncryption.createHandshakeJwt(server, token);
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        String headerJson = new String(Base64.getUrlDecoder().decode(pad(parts[0])), StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("ES384"));
        assertTrue(headerJson.contains("x5u"));
        String payloadJson = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
        assertTrue(payloadJson.contains("salt"));

        SecretKey serverSecret = BedrockEncryption.getSecretKey(server.getPrivate(), client.getPublic(), token);
        SecretKey clientSecret = BedrockEncryption.getSecretKey(client.getPrivate(), server.getPublic(), token);
        assertArrayEquals(serverSecret.getEncoded(), clientSecret.getEncoded());
        assertEquals(32, serverSecret.getEncoded().length);
    }

    @Test
    void ctrPeerCipherRoundTripWithTrailer() throws Exception {
        KeyPair a = BedrockEncryption.createKeyPair();
        KeyPair b = BedrockEncryption.createKeyPair();
        byte[] token = BedrockEncryption.generateRandomToken();
        SecretKey key = BedrockEncryption.getSecretKey(a.getPrivate(), b.getPublic(), token);

        BedrockEncryption.PeerCipher enc = new BedrockEncryption.PeerCipher(key, true);
        BedrockEncryption.PeerCipher dec = new BedrockEncryption.PeerCipher(key, true);

        ByteBuf plain = Unpooled.wrappedBuffer("hello-bedrock-batch".getBytes(StandardCharsets.UTF_8));
        ByteBuf cipher = enc.encrypt(plain);
        assertTrue(cipher.readableBytes() > plain.readableBytes());
        ByteBuf out = dec.decrypt(cipher);
        byte[] got = new byte[out.readableBytes()];
        out.readBytes(got);
        assertEquals("hello-bedrock-batch", new String(got, StandardCharsets.UTF_8));
        assertTrue(dec.inboundSynchronized());
        out.release();
        cipher.release();
    }

    @Test
    void cfb8PeerCipherRoundTrip() throws Exception {
        KeyPair a = BedrockEncryption.createKeyPair();
        KeyPair b = BedrockEncryption.createKeyPair();
        byte[] token = BedrockEncryption.generateRandomToken();
        SecretKey key = BedrockEncryption.getSecretKey(a.getPrivate(), b.getPublic(), token);
        BedrockEncryption.PeerCipher enc = new BedrockEncryption.PeerCipher(key, false);
        BedrockEncryption.PeerCipher dec = new BedrockEncryption.PeerCipher(key, false);
        ByteBuf plain = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
        ByteBuf cipher = enc.encrypt(plain);
        ByteBuf out = dec.decrypt(cipher);
        byte[] got = new byte[out.readableBytes()];
        out.readBytes(got);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, got);
        out.release();
        cipher.release();
    }

    @Test
    void badTrailerDoesNotDesyncCtrStream() throws Exception {
        KeyPair a = BedrockEncryption.createKeyPair();
        KeyPair b = BedrockEncryption.createKeyPair();
        byte[] token = BedrockEncryption.generateRandomToken();
        SecretKey key = BedrockEncryption.getSecretKey(a.getPrivate(), b.getPublic(), token);

        BedrockEncryption.PeerCipher enc = new BedrockEncryption.PeerCipher(key, true);
        BedrockEncryption.PeerCipher dec = new BedrockEncryption.PeerCipher(key, true);

        // Plaintext leftover (Login retransmit) must not advance CTR.
        ByteBuf leftover = Unpooled.wrappedBuffer(new byte[64]);
        assertThrows(BedrockEncryption.InvalidEncryptionTrailerException.class,
                () -> dec.decrypt(leftover));
        assertFalse(dec.inboundSynchronized());

        ByteBuf plain = Unpooled.wrappedBuffer(new byte[]{(byte) 0xff, 4, 0x04, 0, 0, 0});
        ByteBuf cipher = enc.encrypt(plain);
        ByteBuf out = dec.decrypt(cipher);
        byte[] got = new byte[out.readableBytes()];
        out.readBytes(got);
        assertArrayEquals(new byte[]{(byte) 0xff, 4, 0x04, 0, 0, 0}, got);
        assertTrue(dec.inboundSynchronized());
        out.release();
        cipher.release();
        leftover.release();
    }

    @Test
    void encryptedGarbageMustFailTrailerNotLookLikeBatch() throws Exception {
        KeyPair a = BedrockEncryption.createKeyPair();
        KeyPair b = BedrockEncryption.createKeyPair();
        byte[] token = BedrockEncryption.generateRandomToken();
        SecretKey key = BedrockEncryption.getSecretKey(a.getPrivate(), b.getPublic(), token);
        BedrockEncryption.PeerCipher dec = new BedrockEncryption.PeerCipher(key, true);

        byte[] fake = new byte[102];
        new java.security.SecureRandom().nextBytes(fake);
        ByteBuf bogus = Unpooled.wrappedBuffer(fake);
        assertThrows(BedrockEncryption.InvalidEncryptionTrailerException.class,
                () -> dec.decrypt(bogus));
        assertFalse(dec.inboundSynchronized());
        bogus.release();
    }

    private static String pad(String s) {
        int m = s.length() % 4;
        if (m == 0) {
            return s;
        }
        return s + "====".substring(m);
    }
}
