package com.yapcore.link.bedrock.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Geyser / Cloudburst {@code EncryptionUtils} login encryption (clean-room).
 *
 * <p>Flow matches Geyser {@code LoginEncryptionUtils.startEncryptionHandshake}:
 * secp384r1 ECDH server keypair → random 16-byte salt → ES384 handshake JWT →
 * AES secret = SHA-256(salt ‖ ECDH shared) → AES/CTR (proto ≥428) or AES/CFB8.
 *
 * <p>Inbound decrypt is <em>speculative</em>: trailer is always verified on a trial
 * cipher so plaintext leftovers (Login retransmits after {@code enableEncryption})
 * do not advance AES-CTR and permanently desync the session.
 * Callers MUST fall through to plaintext / uncompressed-race on trailer miss —
 * never drop after inboundSynchronized (that caused IC-90).
 */
public final class BedrockEncryption {

    /** Bedrock_v428 — CTR mode replaces CFB8. */
    public static final int CTR_PROTOCOL_FLOOR = 428;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final KeyPairGenerator KEY_PAIR_GEN;

    static {
        // Ensure secp384r1 is available for ECDH (same property Geyser EncryptionUtils sets).
        String namedGroups = System.getProperty("jdk.tls.namedGroups");
        System.setProperty("jdk.tls.namedGroups",
                namedGroups == null || namedGroups.isEmpty() ? "secp384r1" : namedGroups + ",secp384r1");
        try {
            KEY_PAIR_GEN = KeyPairGenerator.getInstance("EC");
            KEY_PAIR_GEN.initialize(new ECGenParameterSpec("secp384r1"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private BedrockEncryption() {
    }

    public static KeyPair createKeyPair() {
        return KEY_PAIR_GEN.generateKeyPair();
    }

    public static byte[] generateRandomToken() {
        byte[] token = new byte[16];
        SECURE_RANDOM.nextBytes(token);
        return token;
    }

    public static PublicKey parseKey(String b64Spki) throws Exception {
        byte[] spki = Base64.getDecoder().decode(b64Spki.replace("\n", "").replace("\r", "").trim());
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
    }

    /**
     * {@code SHA-256(token ‖ ECDH(localPriv, remotePub))} → AES-256 key.
     */
    public static SecretKey getSecretKey(PrivateKey localPrivateKey, PublicKey remotePublicKey, byte[] token)
            throws Exception {
        byte[] sharedSecret = ecdhSecret(localPrivateKey, remotePublicKey);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(token);
        digest.update(sharedSecret);
        return new SecretKeySpec(digest.digest(), "AES");
    }

    private static byte[] ecdhSecret(PrivateKey localPrivateKey, PublicKey remotePublicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(localPrivateKey);
        agreement.doPhase(remotePublicKey, true);
        return agreement.generateSecret();
    }

    /**
     * ES384 JWT: header {@code alg=ES384,x5u=<server SPKI b64>}, claims {@code salt=<token b64>}.
     */
    public static String createHandshakeJwt(KeyPair serverKeyPair, byte[] token) throws Exception {
        String x5u = Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
        String headerJson = "{\"alg\":\"ES384\",\"x5u\":\"" + x5u + "\"}";
        String payloadJson = "{\"salt\":\"" + Base64.getEncoder().encodeToString(token) + "\"}";
        String header = b64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = b64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        byte[] signingInput = (header + "." + payload).getBytes(StandardCharsets.US_ASCII);
        Signature sig = Signature.getInstance("SHA384withECDSA");
        sig.initSign(serverKeyPair.getPrivate());
        sig.update(signingInput);
        byte[] joseSig = derToJoseEs(sig.sign(), 48);
        return header + "." + payload + "." + b64Url(joseSig);
    }

    /**
     * @param useCtr {@code true} for protocol ≥428 (AES/CTR); else AES/CFB8
     */
    public static Cipher createCipher(boolean useCtr, boolean encrypt, SecretKey key) throws Exception {
        byte[] iv;
        String transformation;
        if (useCtr) {
            iv = new byte[16];
            System.arraycopy(key.getEncoded(), 0, iv, 0, 12);
            iv[15] = 2;
            transformation = "AES/CTR/NoPadding";
        } else {
            iv = Arrays.copyOf(key.getEncoded(), 16);
            transformation = "AES/CFB8/NoPadding";
        }
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher;
    }

    /** Trailer mismatch — ciphertext was not produced with this peer key/counter. */
    public static final class InvalidEncryptionTrailerException extends Exception {
        public InvalidEncryptionTrailerException(String message) {
            super(message);
        }
    }

    /** Per-peer encrypt/decrypt state (Cloudburst BedrockEncryptionEncoder/Decoder). */
    public static final class PeerCipher {
        private final SecretKey key;
        private final boolean useCtr;
        private Cipher encrypt;
        private Cipher decrypt;
        /** Bytes successfully fed through the inbound cipher (committed only). */
        private long recvCipherBytes;
        private final AtomicLong sendCounter = new AtomicLong();
        private final AtomicLong recvCounter = new AtomicLong();
        /** True after the first inbound batch with a valid trailer. */
        private volatile boolean inboundSynchronized;

        public PeerCipher(SecretKey key, boolean useCtr) throws Exception {
            this.key = key;
            this.useCtr = useCtr;
            this.encrypt = createCipher(useCtr, true, key);
            this.decrypt = createCipher(useCtr, false, key);
        }

        public SecretKey key() {
            return key;
        }

        public boolean inboundSynchronized() {
            return inboundSynchronized;
        }

        /**
         * Encrypt plaintext batch (compression already applied). Appends 8-byte SHA-256 trailer
         * then AES-streams the whole buffer (Cloudburst encoder).
         */
        public ByteBuf encrypt(ByteBuf plain) throws Exception {
            byte[] trailer = generateTrailer(plain, key, sendCounter.getAndIncrement());
            byte[] in = new byte[plain.readableBytes() + 8];
            plain.getBytes(plain.readerIndex(), in, 0, plain.readableBytes());
            System.arraycopy(trailer, 0, in, plain.readableBytes(), 8);
            byte[] out = streamCrypt(encrypt, in);
            return Unpooled.wrappedBuffer(out);
        }

        /**
         * Decrypt ciphertext batch; always verifies the 8-byte trailer.
         * On failure the inbound CTR/CFB state is left unchanged (speculative trial).
         */
        public ByteBuf decrypt(ByteBuf cipherText) throws Exception {
            if (cipherText.readableBytes() < 9) {
                throw new IllegalArgumentException("encrypted batch too short");
            }
            byte[] in = new byte[cipherText.readableBytes()];
            cipherText.getBytes(cipherText.readerIndex(), in);

            Cipher trial;
            if (useCtr) {
                trial = createCipher(true, false, key);
                skipKeystream(trial, recvCipherBytes);
            } else if (!inboundSynchronized) {
                // CFB8 before first sync: trial from a fresh cipher (offset must be 0).
                trial = createCipher(false, false, key);
            } else {
                // CFB8 after sync cannot fork cheaply — decrypt on live cipher; on failure re-init
                // from scratch is impossible without replay, so require valid trailer.
                trial = decrypt;
            }

            byte[] out = streamCrypt(trial, in);
            int plainLen = out.length - 8;
            ByteBuf plainSlice = Unpooled.wrappedBuffer(out, 0, plainLen);
            byte[] expected = generateTrailer(plainSlice, key, recvCounter.get());
            plainSlice.release();
            byte[] actual = Arrays.copyOfRange(out, plainLen, out.length);
            if (!Arrays.equals(expected, actual)) {
                if (!useCtr && inboundSynchronized && trial == decrypt) {
                    // Live CFB8 advanced — session is corrupt; surface as trailer error.
                    throw new InvalidEncryptionTrailerException("Invalid encryption trailer (CFB8 desync)");
                }
                throw new InvalidEncryptionTrailerException("Invalid encryption trailer");
            }

            // Commit
            recvCounter.getAndIncrement();
            recvCipherBytes += in.length;
            if (useCtr || !inboundSynchronized) {
                decrypt = trial;
            }
            inboundSynchronized = true;
            return Unpooled.copiedBuffer(out, 0, plainLen);
        }
    }

    /** Advance AES/CTR (or CFB) by {@code bytes} without caring about plaintext. */
    static void skipKeystream(Cipher cipher, long bytes) throws Exception {
        if (bytes <= 0) {
            return;
        }
        byte[] buf = new byte[(int) Math.min(4096, bytes)];
        long left = bytes;
        while (left > 0) {
            int n = (int) Math.min(buf.length, left);
            // CTR: decrypt(zeros) consumes keystream. CFB8: same for skip-from-start only.
            cipher.update(buf, 0, n);
            left -= n;
        }
    }

    private static byte[] streamCrypt(Cipher cipher, byte[] in) throws Exception {
        byte[] out = new byte[in.length];
        ByteBuffer inBuf = ByteBuffer.wrap(in);
        ByteBuffer outBuf = ByteBuffer.wrap(out);
        while (inBuf.hasRemaining()) {
            int wrote = cipher.update(inBuf, outBuf);
            if (wrote == 0 && inBuf.hasRemaining()) {
                // Should not happen for CTR/CFB8 NoPadding with equal-sized buffers.
                throw new IllegalStateException("Cipher.update stalled with "
                        + inBuf.remaining() + " bytes remaining");
            }
        }
        return out;
    }

    static byte[] generateTrailer(ByteBuf buf, SecretKey key, long counterValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer counterBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        counterBuf.putLong(counterValue);
        counterBuf.flip();
        digest.update(counterBuf);
        byte[] payload = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), payload);
        digest.update(payload);
        digest.update(key.getEncoded());
        return Arrays.copyOf(digest.digest(), 8);
    }

    /** DER ECDSA → JOSE R‖S (fixed component length). */
    static byte[] derToJoseEs(byte[] der, int componentLen) {
        // SEQUENCE { INTEGER r, INTEGER s }
        int idx = 0;
        if (der[idx++] != 0x30) {
            throw new IllegalArgumentException("not DER SEQUENCE");
        }
        idx = skipDerLength(der, idx);
        if (der[idx++] != 0x02) {
            throw new IllegalArgumentException("expected INTEGER r");
        }
        int rLen = readDerLength(der, idx);
        idx += derLengthBytes(der, idx);
        byte[] r = Arrays.copyOfRange(der, idx, idx + rLen);
        idx += rLen;
        if (der[idx++] != 0x02) {
            throw new IllegalArgumentException("expected INTEGER s");
        }
        int sLen = readDerLength(der, idx);
        idx += derLengthBytes(der, idx);
        byte[] s = Arrays.copyOfRange(der, idx, idx + sLen);
        byte[] out = new byte[componentLen * 2];
        copyRight(r, out, 0, componentLen);
        copyRight(s, out, componentLen, componentLen);
        return out;
    }

    private static int skipDerLength(byte[] der, int idx) {
        return idx + derLengthBytes(der, idx);
    }

    private static int derLengthBytes(byte[] der, int idx) {
        int b = der[idx] & 0xff;
        if ((b & 0x80) == 0) {
            return 1;
        }
        return 1 + (b & 0x7f);
    }

    private static int readDerLength(byte[] der, int idx) {
        int b = der[idx] & 0xff;
        if ((b & 0x80) == 0) {
            return b;
        }
        int n = b & 0x7f;
        int len = 0;
        for (int i = 0; i < n; i++) {
            len = (len << 8) | (der[idx + 1 + i] & 0xff);
        }
        return len;
    }

    private static void copyRight(byte[] src, byte[] dest, int destOff, int len) {
        int srcOff = 0;
        int copy = src.length;
        // Strip leading zero sign byte
        if (copy > 0 && src[0] == 0) {
            srcOff = 1;
            copy--;
        }
        if (copy > len) {
            srcOff += copy - len;
            copy = len;
        }
        System.arraycopy(src, srcOff, dest, destOff + (len - copy), copy);
    }

    private static String b64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
