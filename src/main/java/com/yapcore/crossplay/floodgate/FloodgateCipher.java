package com.yapcore.crossplay.floodgate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Clean-room Floodgate AES-GCM cipher (compatible with GeyserMC Floodgate {@code key.pem}).
 * Wire format: {@code ^Floodgate^}{@code (VERSION+MAGIC)} + Base64(IV) + {@code !} + Base64(ciphertext).
 */
public final class FloodgateCipher {

    public static final String IDENTIFIER = "^Floodgate^";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int MAGIC = 0x3E;
    private static final int VERSION = 0;
    private static final byte SPLITTER = 0x21; // '!'
    private static final String HEADER = IDENTIFIER + (char) (VERSION + MAGIC);

    private final byte[] key;

    public FloodgateCipher(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("Floodgate key must be 16/24/32 bytes (AES)");
        }
        this.key = Arrays.copyOf(key, key.length);
    }

    /** Load raw key bytes from Floodgate {@code key.pem} (file contents used as AES key material). */
    public static FloodgateCipher fromKeyFile(Path keyFile) throws Exception {
        byte[] raw = Files.readAllBytes(keyFile);
        // Floodgate uses the file bytes directly; trim trailing newline if present for PEM-like files
        if (raw.length > 32) {
            // Some installs store a PEM wrapper — try first 16/24/32 raw, else SHA-256 of file
            if (raw.length == 16 || raw.length == 24 || raw.length == 32) {
                return new FloodgateCipher(raw);
            }
            // Prefer exact AES sizes from start; otherwise digest
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return new FloodgateCipher(md.digest(raw));
        }
        return new FloodgateCipher(raw);
    }

    public static boolean looksLikeFloodgatePayload(String value) {
        return value != null && value.startsWith(IDENTIFIER);
    }

    public static int versionOf(String value) {
        if (!looksLikeFloodgatePayload(value) || value.length() < IDENTIFIER.length() + 1) {
            return -1;
        }
        int marker = value.charAt(IDENTIFIER.length());
        return marker - MAGIC;
    }

    public String decryptToString(byte[] cipherTextWithHeader) throws Exception {
        return new String(decrypt(cipherTextWithHeader), StandardCharsets.UTF_8);
    }

    public byte[] decrypt(byte[] cipherTextWithHeader) throws Exception {
        if (cipherTextWithHeader == null || cipherTextWithHeader.length < HEADER.length() + 2) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        String asString = new String(cipherTextWithHeader, StandardCharsets.UTF_8);
        if (!asString.startsWith(HEADER)) {
            throw new IllegalArgumentException("invalid Floodgate header");
        }
        byte[] data = Arrays.copyOfRange(cipherTextWithHeader, HEADER.getBytes(StandardCharsets.UTF_8).length,
                cipherTextWithHeader.length);
        int split = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == SPLITTER) {
                split = i;
                break;
            }
        }
        if (split < 0) {
            throw new IllegalArgumentException("missing Floodgate splitter");
        }
        byte[] iv = Base64.getDecoder().decode(new String(data, 0, split, StandardCharsets.UTF_8));
        byte[] ct = Base64.getDecoder().decode(new String(data, split + 1, data.length - split - 1, StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ct);
    }

    public byte[] encrypt(byte[] plain) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain);
        String body = Base64.getEncoder().encodeToString(iv)
                + (char) SPLITTER
                + Base64.getEncoder().encodeToString(ct);
        return (HEADER + body).getBytes(StandardCharsets.UTF_8);
    }
}
