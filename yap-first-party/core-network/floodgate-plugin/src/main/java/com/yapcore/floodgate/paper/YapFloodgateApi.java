package com.yapcore.floodgate.paper;

import java.util.UUID;
import java.util.function.Predicate;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Grim-facing Floodgate API: treat YaP-recognized Bedrock UUIDs as Floodgate players.
 */
final class YapFloodgateApi implements FloodgateApi {

    private final Predicate<UUID> bedrockCheck;

    YapFloodgateApi(Predicate<UUID> bedrockCheck) {
        this.bedrockCheck = bedrockCheck != null ? bedrockCheck : uuid -> false;
    }

    @Override
    public boolean isFloodgatePlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (isFloodgateId(uuid)) {
            return true;
        }
        return bedrockCheck.test(uuid);
    }

    @Override
    public boolean isFloodgateId(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }
}
