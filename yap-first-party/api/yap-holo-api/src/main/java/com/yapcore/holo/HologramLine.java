package com.yapcore.holo;

import java.util.Locale;
import java.util.Objects;

/** One hologram row: text, floating item, or named animation. */
public final class HologramLine {

    public enum Kind {
        TEXT,
        ITEM,
        ANIM
    }

    private final Kind kind;
    private final String value;

    private HologramLine(Kind kind, String value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = value == null ? "" : value;
    }

    public static HologramLine text(String text) {
        return new HologramLine(Kind.TEXT, text == null ? "" : text);
    }

    public static HologramLine item(String material) {
        return new HologramLine(Kind.ITEM, material == null ? "STONE" : material.trim());
    }

    public static HologramLine anim(String name) {
        return new HologramLine(Kind.ANIM, name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Command/YAML form: {@code #ICON:DIAMOND}, {@code #ITEM:diamond_sword}, {@code #ANIM:wave},
     * or ordinary {@code &6Welcome %player_name%}.
     */
    public static HologramLine parse(String raw) {
        if (raw == null) {
            return text("");
        }
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, "#ICON:", 0, 6) || trimmed.regionMatches(true, 0, "#ITEM:", 0, 6)) {
            int colon = trimmed.indexOf(':');
            String mat = colon < 0 ? "STONE" : trimmed.substring(colon + 1).trim();
            return item(mat.isEmpty() ? "STONE" : mat);
        }
        if (trimmed.regionMatches(true, 0, "#ANIM:", 0, 6)) {
            String name = trimmed.substring(6).trim();
            return anim(name.isEmpty() ? "wave" : name);
        }
        return text(raw);
    }

    public Kind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public String serialize() {
        return switch (kind) {
            case ITEM -> "#ICON:" + value;
            case ANIM -> "#ANIM:" + value;
            case TEXT -> value;
        };
    }

    @Override
    public String toString() {
        return serialize();
    }
}
