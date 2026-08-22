package com.yapcore.link.forwarding;

import com.yapcore.link.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Velocity-compatible modern player-info forwarding ({@code velocity:player_info}).
 * Wire format matches PaperMC Velocity {@code PlayerDataForwarding}.
 */
public final class ModernForwarding {

    public static final String CHANNEL = "velocity:player_info";
    public static final int MODERN_DEFAULT = 1;
    public static final int MODERN_WITH_KEY = 2;
    public static final int MODERN_WITH_KEY_V2 = 3;
    public static final int MODERN_LAZY_SESSION = 4;

    private static final String HMAC = "HmacSHA256";

    private ModernForwarding() {
    }

    public record Property(String name, String value, String signature) {
    }

    /**
     * Build signed forwarding payload for Login Plugin Response.
     * Uses {@link #MODERN_DEFAULT} (v1) — sufficient for offline backends and most Folia setups.
     */
    public static ByteBuf createForwardingData(
            byte[] secret,
            String clientAddress,
            UUID playerId,
            String username,
            List<Property> properties
    ) {
        ByteBuf forwarded = Unpooled.buffer(256);
        try {
            McCodec.writeVarInt(forwarded, MODERN_DEFAULT);
            McCodec.writeString(forwarded, clientAddress == null ? "127.0.0.1" : clientAddress);
            McCodec.writeUuid(forwarded, playerId);
            McCodec.writeString(forwarded, username);
            List<Property> props = properties == null ? List.of() : properties;
            McCodec.writeVarInt(forwarded, props.size());
            for (Property p : props) {
                McCodec.writeString(forwarded, p.name());
                McCodec.writeString(forwarded, p.value());
                boolean hasSig = p.signature() != null && !p.signature().isEmpty();
                forwarded.writeBoolean(hasSig);
                if (hasSig) {
                    McCodec.writeString(forwarded, p.signature());
                }
            }

            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            // Sign the contiguous payload bytes (same as Velocity: array + offset + readable)
            byte[] body = new byte[forwarded.readableBytes()];
            forwarded.getBytes(forwarded.readerIndex(), body);
            byte[] sig = mac.doFinal(body);

            return Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(sig), forwarded);
        } catch (NoSuchAlgorithmException e) {
            forwarded.release();
            throw new AssertionError(e);
        } catch (InvalidKeyException e) {
            forwarded.release();
            throw new IllegalStateException("Bad forwarding secret", e);
        }
    }

    public static byte[] secretBytes(String secret) {
        if (secret == null) {
            return new byte[0];
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
