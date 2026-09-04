package com.yapcore.web.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Permission command builders and sanitize helpers for dashboard access API. */
final class DashboardAccessPermsCommands {

    private DashboardAccessPermsCommands() {
    }

    static String permsCommand(String action, Map<String, String> body) {
        String player = sanitizeToken(body.getOrDefault("player", "").trim());
        return switch (action) {
            case "reload" -> "yapperm reload";
            case "applypack" -> "yapperm applypack";
            case "dump" -> "yapperm dump";
            case "user-info" -> player.isEmpty() ? null : "yapperm user " + player + " info";
            case "set-group" -> {
                String g = sanitizeToken(body.getOrDefault("group", "default"));
                yield player.isEmpty() || g.isEmpty() ? null : "yapperm user " + player + " parent set " + g;
            }
            case "add-group" -> {
                String g = sanitizeToken(body.getOrDefault("group", ""));
                yield player.isEmpty() || g.isEmpty() ? null : "yapperm user " + player + " parent add " + g;
            }
            case "remove-group" -> {
                String g = sanitizeToken(body.getOrDefault("group", ""));
                yield player.isEmpty() || g.isEmpty() ? null : "yapperm user " + player + " parent remove " + g;
            }
            case "promote" -> trackStepCommand("promote", player, body);
            case "demote" -> trackStepCommand("demote", player, body);
            case "group-info" -> {
                String g = sanitizeToken(body.getOrDefault("group", player));
                yield g.isEmpty() ? null : "yapperm group info " + g;
            }
            case "group-list" -> "yapperm group list";
            case "track-info" -> {
                String track = sanitizeToken(body.getOrDefault("track", "yap"));
                yield track.isEmpty() ? null : "yapperm track info " + track;
            }
            case "track-list" -> "yapperm track list";
            case "group-perm" -> groupPermSet(body);
            case "group-perm-unset", "revoke-group-perm" -> groupPermUnset(body);
            case "user-perm" -> userPermSet(body, player);
            case "user-perm-unset", "revoke-user-perm" -> userPermUnset(body, player);
            default -> null;
        };
    }

    /** Build {@code promote|demote <player> [track]}. */
    static String trackStepCommand(String verb, String player, Map<String, String> body) {
        if (player.isEmpty()) {
            return null;
        }
        String track = sanitizeToken(body.getOrDefault("track", "").trim());
        return track.isEmpty() ? verb + " " + player : verb + " " + player + " " + track;
    }

    static String userPermSet(Map<String, String> body, String player) {
        String node = sanitizeNode(body.getOrDefault("node", ""));
        String val = normalizeBool(body.getOrDefault("value", "true"));
        if (player.isEmpty() || node.isEmpty()) {
            return null;
        }
        return appendNodeContext("yapperm user " + player + " permission set " + node + " " + val, body);
    }

    static String userPermUnset(Map<String, String> body, String player) {
        String node = sanitizeNode(body.getOrDefault("node", ""));
        if (player.isEmpty() || node.isEmpty()) {
            return null;
        }
        return appendNodeContext("yapperm user " + player + " permission unset " + node, body, false);
    }

    static String groupPermSet(Map<String, String> body) {
        String g = sanitizeToken(body.getOrDefault("group", ""));
        String node = sanitizeNode(body.getOrDefault("node", ""));
        String val = normalizeBool(body.getOrDefault("value", "true"));
        if (g.isEmpty() || node.isEmpty()) {
            return null;
        }
        return appendNodeContext("yapperm group permission set " + g + " " + node + " " + val, body);
    }

    static String groupPermUnset(Map<String, String> body) {
        String g = sanitizeToken(body.getOrDefault("group", ""));
        String node = sanitizeNode(body.getOrDefault("node", ""));
        if (g.isEmpty() || node.isEmpty()) {
            return null;
        }
        return appendNodeContext("yapperm group permission unset " + g + " " + node, body, false);
    }

    /**
     * Append LuckPerms-style trailing modifiers that YaPPerms already understands:
     * duration ({@code 1d}, {@code 7d}), {@code world=}, {@code server=}.
     */
    static String appendNodeContext(String baseCmd, Map<String, String> body) {
        return appendNodeContext(baseCmd, body, true);
    }

    static String appendNodeContext(String baseCmd, Map<String, String> body, boolean includeDuration) {
        StringBuilder sb = new StringBuilder(baseCmd);
        if (includeDuration) {
            String duration = firstNonBlank(body.get("duration"), body.get("expires"), body.get("temp"));
            if (duration != null) {
                String token = sanitizeToken(duration);
                if (!token.isEmpty() && !isPermanentDuration(token)) {
                    sb.append(' ').append(token);
                }
            }
        }
        String world = sanitizeToken(body.getOrDefault("world", "").trim());
        if (!world.isEmpty()) {
            sb.append(" world=").append(world);
        }
        String server = sanitizeToken(body.getOrDefault("server", "").trim());
        if (!server.isEmpty()) {
            sb.append(" server=").append(server);
        }
        return sb.toString();
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    static boolean isPermanentDuration(String raw) {
        String t = raw.toLowerCase();
        return t.isEmpty() || t.equals("0") || t.equals("perm") || t.equals("permanent")
                || t.equals("forever") || t.equals("*") || t.equals("none");
    }

    static String normalizeBool(String raw) {
        if (raw == null || raw.isBlank()) {
            return "true";
        }
        String t = raw.trim().toLowerCase();
        if (t.equals("false") || t.equals("deny") || t.equals("0") || t.equals("no")) {
            return "false";
        }
        return "true";
    }

    /** Permission nodes: allow dots/wildcards; reject shell-breaking chars. */
    static String sanitizeNode(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty() || t.indexOf(' ') >= 0 || t.indexOf('"') >= 0 || t.indexOf('\'') >= 0
                || t.indexOf(';') >= 0 || t.indexOf('|') >= 0 || t.indexOf('&') >= 0) {
            return "";
        }
        return t;
    }

    static String sanitizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == ';' || c == '|' || c == '&'
                    || c == '=' || c == '\n' || c == '\r') {
                return "";
            }
        }
        return t;
    }

    static List<String> parseNodeList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,\\n]")) {
            String t = part.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,\\n]")) {
            String t = part.trim().toLowerCase();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
