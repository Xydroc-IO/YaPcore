package org.geysermc.floodgate.api;

import java.util.UUID;

/**
 * Minimal Floodgate API surface used by GrimAC ({@code GeyserUtil.isBedrockPlayer}).
 *
 * <p>Full Floodgate is not required — YaPFloodgate registers an implementation so Grim
 * exempts Bedrock (MSB=0 / remembered) players from Reach / Hitboxes cancels.
 */
public interface FloodgateApi {

    static FloodgateApi getInstance() {
        return InstanceHolder.getApi();
    }

    boolean isFloodgatePlayer(UUID uuid);

    /** Floodgate offline UUID shape: most-significant bits == 0. */
    boolean isFloodgateId(UUID uuid);
}
