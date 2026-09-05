package com.yapcore.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Pure helpers for console-channel inbound auth / whitelist and outbound log-level filter.
 */
public final class ConsoleChannelRules {

    private ConsoleChannelRules() {
    }

    /**
     * Empty {@code inboundRoles} → require Discord Administrator.
     * Non-empty → any matching role snowflake.
     */
    public static boolean memberAllowedInbound(Member member, List<String> inboundRoles) {
        if (member == null) {
            return false;
        }
        if (inboundRoles == null || inboundRoles.isEmpty()) {
            return member.hasPermission(Permission.ADMINISTRATOR);
        }
        return TextCommandParser.hasAnyRole(memberRoleIds(member), inboundRoles);
    }

    /**
     * Empty whitelist → allow all commands (dangerous).
     * Non-empty → first token must match (case-insensitive).
     */
    public static boolean isCommandAllowed(String consoleCommand, List<String> whitelist) {
        if (consoleCommand == null || consoleCommand.isBlank()) {
            return false;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        String first = consoleCommand.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        for (String allowed : whitelist) {
            if (allowed != null && first.equals(allowed.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a JUL level should be forwarded given configured filter names
     * ({@code INFO}, {@code WARN}/{@code WARNING}, {@code SEVERE}, …).
     */
    public static boolean levelAllowed(Level level, List<String> filter) {
        if (level == null || filter == null || filter.isEmpty()) {
            return false;
        }
        String name = level.getName();
        for (String f : filter) {
            if (f == null || f.isBlank()) {
                continue;
            }
            String want = f.trim().toUpperCase(Locale.ROOT);
            if (want.equals(name)) {
                return true;
            }
            if ("WARN".equals(want) && Level.WARNING.getName().equals(name)) {
                return true;
            }
            if ("WARNING".equals(want) && "WARN".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> memberRoleIds(Member member) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (var role : member.getRoles()) {
            ids.add(role.getId());
        }
        return ids;
    }
}
