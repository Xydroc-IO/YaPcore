package com.yapcore.games;

import java.util.Locale;
import java.util.Objects;

public record GameModeId(String id) {

    public GameModeId {
        Objects.requireNonNull(id, "id");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("mode id empty");
        }
    }

    public static GameModeId of(String raw) {
        return new GameModeId(raw);
    }

    @Override
    public String toString() {
        return id;
    }
}
