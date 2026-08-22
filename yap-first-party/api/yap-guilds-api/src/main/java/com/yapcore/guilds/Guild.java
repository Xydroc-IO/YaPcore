package com.yapcore.guilds;

import java.time.Instant;
import java.util.UUID;

public record Guild(
        long id,
        String name,
        String tag,
        UUID leaderId,
        int level,
        long xp,
        String description,
        String motd,
        GuildJoinMode joinMode,
        double bankBalance,
        GuildHome home,
        Instant createdAt) {
}
