package com.yapcore.protocol.floodgate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Decode Floodgate identity from proxy→backend handshake hostname. */
public final class FloodgateHostnameDecoder {

    private final FloodgateCipher cipher;

    public FloodgateHostnameDecoder(FloodgateCipher cipher) {
        this.cipher = cipher;
    }

    public record HostnameSplit(String floodgatePayload, String cleanHostname) {
    }

    public static HostnameSplit separateHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return new HostnameSplit(null, hostname == null ? "" : hostname);
        }
        String[] items = hostname.split("\0", -1);
        String floodgate = null;
        StringBuilder clean = new StringBuilder();
        for (String value : items) {
            if (floodgate == null && FloodgateCipher.looksLikeFloodgatePayload(value)) {
                floodgate = value;
                continue;
            }
            if (clean.length() > 0) {
                clean.append('\0');
            }
            clean.append(value);
        }
        return new HostnameSplit(floodgate, clean.toString());
    }

    public Optional<FloodgateBedrockData> decryptPayload(String floodgatePayload) {
        if (cipher == null || floodgatePayload == null) {
            return Optional.empty();
        }
        try {
            String plain = cipher.decryptToString(floodgatePayload.getBytes(StandardCharsets.UTF_8));
            return Optional.of(FloodgateBedrockData.fromString(plain));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<FloodgateBedrockData> fromHostname(String hostname) {
        return decryptPayload(separateHostname(hostname).floodgatePayload());
    }

    public static Optional<Long> xuidFromFloodgateUuid(UUID uuid) {
        if (uuid == null || uuid.getMostSignificantBits() != 0L) {
            return Optional.empty();
        }
        return Optional.of(uuid.getLeastSignificantBits());
    }
}
