package com.yapcore.discord.link;

import java.util.UUID;

/** Persisted Minecraft ↔ Discord account pairing. */
public record DiscordLink(
        UUID mcUuid,
        String discordId,
        long linkedAtMs,
        boolean verified
) {
}
