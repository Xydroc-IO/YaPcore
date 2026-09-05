package com.yapcore.discord;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure parsing for Discord guild text triggers ({@code playerlist}, {@code !c …}).
 * No Bukkit / JDA dependencies — unit-testable.
 */
public final class TextCommandParser {

    public enum Kind {
        NONE,
        PLAYERLIST,
        CONSOLE
    }

    public record Parsed(Kind kind, String consoleCommand) {
        public static Parsed none() {
            return new Parsed(Kind.NONE, null);
        }

        public static Parsed playerlist() {
            return new Parsed(Kind.PLAYERLIST, null);
        }

        public static Parsed console(String command) {
            return new Parsed(Kind.CONSOLE, command);
        }
    }

    private TextCommandParser() {
    }

    /**
     * @param raw message content (display or raw)
     * @param playerlistEnabled when false, playerlist tokens are ignored
     * @param consolePrefix e.g. {@code !c}; blank disables console triggers
     */
    public static Parsed parse(String raw, boolean playerlistEnabled, String consolePrefix) {
        if (raw == null) {
            return Parsed.none();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Parsed.none();
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (playerlistEnabled && (lower.equals("playerlist") || lower.equals("!playerlist"))) {
            return Parsed.playerlist();
        }
        if (consolePrefix == null || consolePrefix.isBlank()) {
            return Parsed.none();
        }
        String prefix = consolePrefix.trim();
        if (trimmed.length() <= prefix.length()) {
            return Parsed.none();
        }
        if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return Parsed.none();
        }
        char boundary = trimmed.charAt(prefix.length());
        if (!Character.isWhitespace(boundary)) {
            return Parsed.none();
        }
        String cmd = trimmed.substring(prefix.length()).trim();
        if (cmd.isEmpty()) {
            return Parsed.none();
        }
        return Parsed.console(cmd);
    }

    /**
     * First token of a console command must be in the whitelist (case-insensitive).
     * Empty whitelist rejects all.
     */
    public static boolean isConsoleCommandAllowed(String consoleCommand, List<String> whitelist) {
        if (consoleCommand == null || consoleCommand.isBlank()) {
            return false;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        String first = consoleCommand.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        for (String allowed : whitelist) {
            if (allowed != null && first.equals(allowed.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** True when the member has any of the required role snowflakes. Empty required → deny. */
    public static boolean hasAnyRole(Set<String> memberRoleIds, List<String> requiredRoleIds) {
        if (requiredRoleIds == null || requiredRoleIds.isEmpty()) {
            return false;
        }
        if (memberRoleIds == null || memberRoleIds.isEmpty()) {
            return false;
        }
        for (String required : requiredRoleIds) {
            if (required != null && !required.isBlank() && memberRoleIds.contains(required.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars < 1 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    /** Channel topic placeholders: {@code {online}}, {@code {mspt}}, {@code {max}}. */
    public static String applyTopicTemplate(String template, int online, String mspt, int maxPlayers) {
        String t = template == null ? "" : template;
        return t.replace("{online}", Integer.toString(online))
                .replace("{mspt}", mspt == null ? "n/a" : mspt)
                .replace("{max}", Integer.toString(maxPlayers));
    }
}
