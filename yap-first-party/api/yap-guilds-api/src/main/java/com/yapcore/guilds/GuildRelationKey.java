package com.yapcore.guilds;

public final class GuildRelationKey {

    public record Pair(long lowId, long highId) {
    }

    private GuildRelationKey() {
    }

    public static Pair of(long guildA, long guildB) {
        if (guildA == guildB) {
            throw new IllegalArgumentException("same guild");
        }
        return guildA < guildB ? new Pair(guildA, guildB) : new Pair(guildB, guildA);
    }
}
