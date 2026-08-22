package com.yapcore.factions;

/** Normalized faction pair key for ally/enemy relations (faction_id_a &lt; faction_id_b). */
public final class FactionRelationKey {

    private FactionRelationKey() {
    }

    public record Pair(long lowId, long highId) {
    }

    public static Pair of(long factionA, long factionB) {
        if (factionA == factionB) {
            throw new IllegalArgumentException("same faction");
        }
        return factionA < factionB ? new Pair(factionA, factionB) : new Pair(factionB, factionA);
    }
}
