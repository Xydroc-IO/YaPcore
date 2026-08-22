package com.yapcore.protocol.floodgate;

import java.util.Optional;
import java.util.UUID;

/** Decrypted Floodgate Bedrock player blob (12 NUL-separated fields). */
public record FloodgateBedrockData(
        String version,
        String username,
        long xuid,
        int deviceOs,
        String language,
        int uiProfile,
        int inputMode,
        String ip,
        String linkedPlayerRaw,
        boolean fromProxy,
        String subscribeId,
        String verifyCode
) {
    public static final int EXPECTED_FIELDS = 12;

    public static FloodgateBedrockData fromString(String decrypted) {
        if (decrypted == null || decrypted.isBlank()) {
            throw new IllegalArgumentException("empty bedrock data");
        }
        String[] parts = decrypted.split("\0", -1);
        if (parts.length != EXPECTED_FIELDS) {
            throw new IllegalArgumentException("expected " + EXPECTED_FIELDS + " fields, got " + parts.length);
        }
        String username = parts[1];
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("blank username");
        }
        long xuid = Long.parseLong(parts[2]);
        if (xuid == 0L) {
            throw new IllegalArgumentException("xuid cannot be 0");
        }
        return new FloodgateBedrockData(
                parts[0], username, xuid,
                Integer.parseInt(parts[3]), parts[4],
                Integer.parseInt(parts[5]), Integer.parseInt(parts[6]),
                parts[7], parts[8], "1".equals(parts[9]), parts[10], parts[11]
        );
    }

    public UUID floodgateJavaUuid() {
        return FloodgateCipher.uuidFromXuid(Long.toString(xuid));
    }

    public Optional<LinkedJavaAccount> linkedAccount() {
        return LinkedJavaAccount.parse(linkedPlayerRaw);
    }

    public record LinkedJavaAccount(String javaUsername, UUID javaUuid, UUID bedrockUuid) {
        static Optional<LinkedJavaAccount> parse(String raw) {
            if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
                return Optional.empty();
            }
            String[] p = raw.split(";", -1);
            if (p.length != 3 || p[0].isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new LinkedJavaAccount(p[0], UUID.fromString(p[1]), UUID.fromString(p[2])));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
