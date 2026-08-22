package com.yapcore.link.floodgate;

import com.yapcore.protocol.floodgate.FloodgateBedrockData;
import com.yapcore.protocol.floodgate.FloodgateCipher;
import com.yapcore.protocol.floodgate.FloodgateHostnameDecoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Floodgate key forwarding at YaP Link (matches {@code yap-floodgate} on backend). */
public final class FloodgateForwarder {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Floodgate");

    private final FloodgateHostnameDecoder decoder;

    public FloodgateForwarder(Path keyFile) {
        FloodgateHostnameDecoder dec = null;
        if (keyFile != null && Files.isRegularFile(keyFile)) {
            try {
                dec = new FloodgateHostnameDecoder(FloodgateCipher.fromKeyFile(keyFile));
                LOG.info("Floodgate key loaded from " + keyFile);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Floodgate key load failed: " + e.getMessage());
            }
        }
        this.decoder = dec;
    }

    public boolean enabled() {
        return decoder != null;
    }

    public record ResolvedIdentity(UUID uuid, String username, long xuid, boolean linked) {
    }

    /** Parse Floodgate blob from handshake hostname ({@code host\0^Floodgate^…}). */
    public Optional<ResolvedIdentity> resolve(String handshakeHost, UUID loginUuid, String loginName) {
        if (decoder == null || handshakeHost == null) {
            return Optional.empty();
        }
        Optional<FloodgateBedrockData> data = decoder.fromHostname(handshakeHost);
        if (data.isPresent()) {
            FloodgateBedrockData d = data.get();
            UUID javaUuid = d.linkedAccount()
                    .map(FloodgateBedrockData.LinkedJavaAccount::javaUuid)
                    .orElse(d.floodgateJavaUuid());
            String name = d.linkedAccount()
                    .map(FloodgateBedrockData.LinkedJavaAccount::javaUsername)
                    .orElse(loginName != null ? loginName : d.username());
            return Optional.of(new ResolvedIdentity(javaUuid, name, d.xuid(), d.linkedAccount().isPresent()));
        }
        Optional<Long> xuid = FloodgateHostnameDecoder.xuidFromFloodgateUuid(loginUuid);
        if (xuid.isPresent()) {
            return Optional.of(new ResolvedIdentity(
                    loginUuid,
                    loginName != null ? loginName : "BedrockPlayer",
                    xuid.get(),
                    false));
        }
        return Optional.empty();
    }

    /** Re-inject Floodgate payload into backend handshake hostname. */
    public String forwardingHostname(String cleanHost, String floodgatePayload) {
        if (floodgatePayload == null || floodgatePayload.isBlank()) {
            return cleanHost;
        }
        FloodgateHostnameDecoder.HostnameSplit split = FloodgateHostnameDecoder.separateHostname(cleanHost);
        String host = split.cleanHostname();
        if (host == null || host.isEmpty()) {
            host = cleanHost;
        }
        return host + '\0' + floodgatePayload;
    }

    public static String extractFloodgatePayload(String handshakeHost) {
        return FloodgateHostnameDecoder.separateHostname(handshakeHost).floodgatePayload();
    }
}
