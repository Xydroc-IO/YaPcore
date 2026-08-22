package com.yapcore.mmo;

import java.util.Locale;
import java.util.Objects;

/** Stable skill identifier (e.g. {@code mining}). */
public record SkillId(String id) {

    public SkillId {
        Objects.requireNonNull(id, "id");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("skill id empty");
        }
    }

    public static SkillId of(String raw) {
        return new SkillId(raw);
    }

    @Override
    public String toString() {
        return id;
    }
}
