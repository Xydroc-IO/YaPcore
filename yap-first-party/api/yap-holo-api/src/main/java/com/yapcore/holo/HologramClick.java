package com.yapcore.holo;

import java.util.Locale;
import java.util.Objects;

/** Click / punch handler on a hologram (DecentHolograms-class). */
public final class HologramClick {

    public enum Side {
        LEFT,
        RIGHT
    }

    public enum Action {
        CONSOLE,
        PLAYER,
        COMMAND,
        NEXT,
        PREV,
        PAGE
    }

    private final Side side;
    private final Action action;
    private final String value;

    public HologramClick(Side side, Action action, String value) {
        this.side = Objects.requireNonNull(side, "side");
        this.action = Objects.requireNonNull(action, "action");
        this.value = value == null ? "" : value;
    }

    public Side side() {
        return side;
    }

    public Action action() {
        return action;
    }

    public String value() {
        return value;
    }

    /**
     * {@code LEFT:NEXT}, {@code RIGHT:CONSOLE:say hi}, {@code LEFT:PAGE:2}, {@code RIGHT:PLAYER:warp spawn}.
     */
    public static HologramClick parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        Side side = sideOf(parts[0]);
        Action action = actionOf(parts[1]);
        String value = parts.length >= 3 ? parts[2] : "";
        if (action == null) {
            action = Action.PLAYER;
            value = parts[1] + (parts.length >= 3 ? ":" + parts[2] : "");
        }
        return new HologramClick(side, action, value);
    }

    private static Side sideOf(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("LEFT") || s.equals("ATTACK") || s.equals("PUNCH")) {
            return Side.LEFT;
        }
        return Side.RIGHT;
    }

    private static Action actionOf(String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "CONSOLE" -> Action.CONSOLE;
            case "PLAYER", "CMD" -> Action.PLAYER;
            case "COMMAND" -> Action.COMMAND;
            case "NEXT", "NEXT_PAGE" -> Action.NEXT;
            case "PREV", "PREV_PAGE", "BACK" -> Action.PREV;
            case "PAGE" -> Action.PAGE;
            default -> null;
        };
    }

    public String serialize() {
        if (value == null || value.isEmpty()) {
            return side.name() + ":" + action.name();
        }
        return side.name() + ":" + action.name() + ":" + value;
    }
}
