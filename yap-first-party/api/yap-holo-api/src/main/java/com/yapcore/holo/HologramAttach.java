package com.yapcore.holo;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Follow an NPC, player, or any entity. */
public final class HologramAttach {

    public enum Kind {
        NONE,
        ENTITY,
        PLAYER,
        NPC
    }

    private final Kind kind;
    private final String key;
    private final double offsetY;

    private HologramAttach(Kind kind, String key, double offsetY) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = key == null ? "" : key;
        this.offsetY = offsetY;
    }

    public static HologramAttach none() {
        return new HologramAttach(Kind.NONE, "", 0);
    }

    public static HologramAttach entity(UUID uuid, double offsetY) {
        return new HologramAttach(Kind.ENTITY, uuid == null ? "" : uuid.toString(), offsetY);
    }

    public static HologramAttach player(UUID uuid, double offsetY) {
        return new HologramAttach(Kind.PLAYER, uuid == null ? "" : uuid.toString(), offsetY);
    }

    public static HologramAttach npc(String npcId, double offsetY) {
        return new HologramAttach(Kind.NPC, npcId == null ? "" : npcId, offsetY);
    }

    /** {@code npc:shop:1.8}, {@code entity:<uuid>:1.6}, {@code player:<uuid>:2.1} */
    public static HologramAttach parse(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw)) {
            return none();
        }
        String[] parts = raw.split(":");
        if (parts.length < 2) {
            return none();
        }
        Kind kind = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "npc" -> Kind.NPC;
            case "player" -> Kind.PLAYER;
            case "entity" -> Kind.ENTITY;
            default -> Kind.NONE;
        };
        if (kind == Kind.NONE) {
            return none();
        }
        String key = parts[1];
        double offset = 0.25;
        if (parts.length >= 3) {
            try {
                offset = Double.parseDouble(parts[parts.length - 1]);
                if (kind == Kind.ENTITY || kind == Kind.PLAYER) {
                    key = raw.substring(parts[0].length() + 1, raw.lastIndexOf(':'));
                }
            } catch (NumberFormatException e) {
                if (kind == Kind.NPC && parts.length >= 3) {
                    key = parts[1];
                }
            }
        }
        return new HologramAttach(kind, key, offset);
    }

    public Kind kind() {
        return kind;
    }

    public String key() {
        return key;
    }

    public double offsetY() {
        return offsetY;
    }

    public UUID uuidKey() {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String serialize() {
        if (kind == Kind.NONE) {
            return "";
        }
        return kind.name().toLowerCase(Locale.ROOT) + ":" + key + ":" + offsetY;
    }
}
