package com.yapcore.games;

import java.util.Objects;
import java.util.UUID;

public record MatchId(UUID id) {

    public MatchId {
        Objects.requireNonNull(id, "id");
    }

    public static MatchId random() {
        return new MatchId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
