package com.yapcore.npcs.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses NPC interact actions.
 * Formats (semicolon-separated):
 * <ul>
 *   <li>{@code shop:12} — open YaPPlayerData trader GUI #12</li>
 *   <li>{@code warp:spawn} — run {@code /warp spawn} as the player</li>
 *   <li>{@code command:say hi {player}} — console command</li>
 *   <li>{@code player:kit starter} — player runs the command</li>
 * </ul>
 */
public final class NpcActions {

    public enum Kind {
        SHOP,
        WARP,
        COMMAND,
        PLAYER
    }

    public record Action(Kind kind, String value) {
    }

    private NpcActions() {
    }

    public static List<Action> parse(String raw) {
        List<Action> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(";")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon <= 0 || colon >= token.length() - 1) {
                continue;
            }
            String type = token.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            Kind kind = switch (type) {
                case "shop", "trader" -> Kind.SHOP;
                case "warp" -> Kind.WARP;
                case "command", "console", "cmd" -> Kind.COMMAND;
                case "player", "playercmd", "sudo" -> Kind.PLAYER;
                default -> null;
            };
            if (kind != null) {
                out.add(new Action(kind, value));
            }
        }
        return out;
    }
}
