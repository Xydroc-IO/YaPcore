package com.yapcore.floodgate.paper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Self-contained Floodgate decode + registry for Paper behind Velocity. */
final class FloodgateRuntime {

    static final String IDENTIFIER = "^Floodgate^";
    private static final int MAGIC = 0x3E;
    private static final int VERSION = 0;
    private static final byte SPLITTER = 0x21;
    private static final String HEADER = IDENTIFIER + (char) (VERSION + MAGIC);
    private static final int TAG_BITS = 128;

    record BedrockData(
            String version,
            String username,
            long xuid,
            int deviceOs,
            String language,
            String ip,
            String linkedRaw,
            boolean fromProxy
    ) {
        static BedrockData parse(String decrypted) {
            String[] p = decrypted.split("\0", -1);
            if (p.length != 12) {
                throw new IllegalArgumentException("fields=" + p.length);
            }
            long xuid = Long.parseLong(p[2]);
            if (xuid == 0L) {
                throw new IllegalArgumentException("xuid=0");
            }
            return new BedrockData(p[0], p[1], xuid, Integer.parseInt(p[3]), p[4], p[7], p[8], "1".equals(p[9]));
        }

        UUID javaUuid() {
            return linked()
                    .map(Linked::javaUuid)
                    .orElse(new UUID(0L, xuid));
        }

        Optional<Linked> linked() {
            if (linkedRaw == null || linkedRaw.isBlank() || "null".equalsIgnoreCase(linkedRaw)) {
                return Optional.empty();
            }
            String[] t = linkedRaw.split(";", -1);
            if (t.length != 3 || t[0].isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Linked(t[0], UUID.fromString(t[1]), UUID.fromString(t[2])));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        record Linked(String javaUsername, UUID javaUuid, UUID bedrockUuid) {
        }
    }

    record PlayerInfo(
            UUID uuid,
            String name,
            long xuid,
            boolean linked,
            String bedrockUsername,
            String language,
            String ip
    ) {
        boolean isBedrock() {
            return true;
        }
    }

    private final Logger log;
    private final byte[] key; // nullable
    private final ConcurrentHashMap<UUID, PlayerInfo> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerInfo> byName = new ConcurrentHashMap<>();

    FloodgateRuntime(Logger log, Path keyFile) {
        this.log = log;
        byte[] k = null;
        if (keyFile != null && Files.isRegularFile(keyFile)) {
            try {
                k = loadKey(keyFile);
                log.info("Floodgate key loaded from " + keyFile + " (" + k.length + " bytes)");
            } catch (Exception e) {
                log.warning("Could not load Floodgate key: " + e.getMessage());
            }
        } else {
            log.info("No Floodgate key — using UUID(0,xuid) heuristic only");
        }
        this.key = k;
    }

    private static byte[] loadKey(Path file) throws Exception {
        byte[] raw = Files.readAllBytes(file);
        String text = new String(raw, StandardCharsets.US_ASCII).trim();
        if (text.contains("BEGIN")) {
            String b64 = text.replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(b64);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
            // Floodgate sometimes stores encoded secret key structure — use SHA-256
            return java.security.MessageDigest.getInstance("SHA-256").digest(decoded);
        }
        if (raw.length == 16 || raw.length == 24 || raw.length == 32) {
            return raw;
        }
        // Trim trailing newline
        if ((raw.length == 17 || raw.length == 25 || raw.length == 33) && raw[raw.length - 1] == '\n') {
            return Arrays.copyOf(raw, raw.length - 1);
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(raw);
    }

    PlayerInfo remember(UUID uuid, String name, String hostname) {
        PlayerInfo fromHost = tryHostname(hostname);
        if (fromHost != null) {
            put(fromHost);
            return fromHost;
        }
        if (uuid != null && uuid.getMostSignificantBits() == 0L) {
            long xuid = uuid.getLeastSignificantBits();
            PlayerInfo info = new PlayerInfo(uuid, name, xuid, false, stripPrefix(name), "", "");
            put(info);
            return info;
        }
        return null;
    }

    private PlayerInfo tryHostname(String hostname) {
        if (key == null || hostname == null) {
            return null;
        }
        String payload = null;
        for (String part : hostname.split("\0", -1)) {
            if (part.startsWith(IDENTIFIER)) {
                payload = part;
                break;
            }
        }
        if (payload == null) {
            return null;
        }
        try {
            String plain = decrypt(payload.getBytes(StandardCharsets.UTF_8));
            BedrockData data = BedrockData.parse(plain);
            UUID uuid = data.javaUuid();
            String name = data.linked().map(BedrockData.Linked::javaUsername).orElse(data.username());
            return new PlayerInfo(
                    uuid,
                    name,
                    data.xuid(),
                    data.linked().isPresent(),
                    data.username(),
                    data.language(),
                    data.ip()
            );
        } catch (Exception e) {
            log.warning("Floodgate hostname decrypt failed: " + e.getMessage());
            return null;
        }
    }

    private String decrypt(byte[] cipherTextWithHeader) throws Exception {
        String asString = new String(cipherTextWithHeader, StandardCharsets.UTF_8);
        if (!asString.startsWith(HEADER)) {
            throw new IllegalArgumentException("bad header");
        }
        byte[] headerBytes = HEADER.getBytes(StandardCharsets.UTF_8);
        byte[] data = Arrays.copyOfRange(cipherTextWithHeader, headerBytes.length, cipherTextWithHeader.length);
        int split = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == SPLITTER) {
                split = i;
                break;
            }
        }
        if (split < 0) {
            throw new IllegalArgumentException("no splitter");
        }
        byte[] iv = Base64.getDecoder().decode(new String(data, 0, split, StandardCharsets.UTF_8));
        byte[] ct = Base64.getDecoder().decode(new String(data, split + 1, data.length - split - 1, StandardCharsets.UTF_8));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }

    private void put(PlayerInfo info) {
        byUuid.put(info.uuid(), info);
        byName.put(info.name().toLowerCase(), info);
        log.info("Bedrock player " + info.name() + " xuid=" + Long.toUnsignedString(info.xuid())
                + " uuid=" + info.uuid() + " linked=" + info.linked());
    }

    void forget(UUID uuid) {
        PlayerInfo info = byUuid.remove(uuid);
        if (info != null) {
            byName.remove(info.name().toLowerCase());
        }
    }

    Optional<PlayerInfo> get(UUID uuid) {
        return Optional.ofNullable(byUuid.get(uuid));
    }

    Optional<PlayerInfo> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase()));
    }

    boolean isBedrock(UUID uuid) {
        return get(uuid).isPresent() || (uuid != null && uuid.getMostSignificantBits() == 0L);
    }

    private static String stripPrefix(String name) {
        if (name != null && name.startsWith(".") && name.length() > 1) {
            return name.substring(1);
        }
        return name == null ? "" : name;
    }
}
