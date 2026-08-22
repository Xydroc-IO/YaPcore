package com.yapcore.guilds;

import java.util.UUID;

public record GuildMember(long guildId, UUID playerId, GuildRole role, long contributionXp) {
}
