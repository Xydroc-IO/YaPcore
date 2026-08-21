package com.yapcore.crossplay.floodgate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Decode Floodgate identity from Velocity→backend handshake hostname or UUID heuristic.
 * Replaces the Floodgate <em>backend</em> jar when Geyser+Floodgate run on Velocity.
 */
public final class VelocityFloodgateDecoder {

    private static final Logger LOG = Logger.getLogger("YaPcore.VelocityFloodgate");

    private final FloodgateCipher cipher; // nullable — heuristic-only mode

    public VelocityFloodgateDecoder(FloodgateCipher cipher) {
        this.cipher = cipher;
    }

    public record HostnameSplit(String floodgatePayload, String cleanHostname) {
    }

    /** Split {@code host\0^Floodgate^…\0…} as Floodgate Velocity injects. */
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
            LOG.warning("Floodgate decrypt failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<FloodgateBedrockData> fromHostname(String hostname) {
        HostnameSplit split = separateHostname(hostname);
        return decryptPayload(split.floodgatePayload());
    }

    /**
     * Unlinked Floodgate players use {@code UUID(0, xuid)}. Linked accounts use the real Java UUID
     * (heuristic alone cannot recover XUID — need decrypted payload).
     */
    public static Optional<Long> xuidFromFloodgateUuid(UUID uuid) {
        if (uuid == null || uuid.getMostSignificantBits() != 0L) {
            return Optional.empty();
        }
        return Optional.of(uuid.getLeastSignificantBits());
    }

    public static boolean isFloodgateUuid(UUID uuid) {
        return xuidFromFloodgateUuid(uuid).isPresent();
    }

    /** Build / register a {@link FloodgateAuth.Identity} from Velocity join data. */
    public FloodgateAuth.Identity toIdentity(UUID joinUuid, String joinName, String hostname, FloodgateAuth auth) {
        Optional<FloodgateBedrockData> data = fromHostname(hostname);
        if (data.isPresent()) {
            FloodgateBedrockData d = data.get();
            UUID javaUuid = d.linkedAccount()
                    .map(FloodgateBedrockData.LinkedJavaAccount::javaUuid)
                    .orElse(d.floodgateJavaUuid());
            String name = d.linkedAccount()
                    .map(FloodgateBedrockData.LinkedJavaAccount::javaUsername)
                    .orElse(joinName != null ? joinName : d.username());
            return auth.register(new FloodgateAuth.Identity(
                    name,
                    Long.toString(d.xuid()),
                    javaUuid,
                    0,
                    d.linkedAccount().isPresent(),
                    ""
            ));
        }
        Optional<Long> xuid = xuidFromFloodgateUuid(joinUuid);
        if (xuid.isPresent()) {
            return auth.register(new FloodgateAuth.Identity(
                    joinName != null ? joinName : "BedrockPlayer",
                    Long.toUnsignedString(xuid.get()),
                    joinUuid,
                    0,
                    false,
                    ""
            ));
        }
        return null;
    }
}
