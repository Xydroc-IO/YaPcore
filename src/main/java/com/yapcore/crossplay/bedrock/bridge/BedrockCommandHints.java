package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockItemStates;

import java.util.Locale;

/** Best-effort /give and /clear into shadow inventory for BE-only sessions. */
public final class BedrockCommandHints {

    private final BedrockBridgeContext ctx;

    public BedrockCommandHints(BedrockBridgeContext ctx) {
        this.ctx = ctx;
    }

    void applyCommandInventoryHints(String username, String line) {
        if (line == null) {
            return;
        }
        var sync = ctx.paperWorld;
        if (sync != null && sync.hasInjectedPlayer(username)) {
            return;
        }
        String cmd = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        String lower = cmd.toLowerCase(Locale.ROOT);
        ctx.inventory.ensure(username);
        if (lower.equals("clear") || lower.startsWith("clear ")) {
            applyClear(username, cmd);
            return;
        }
        if (lower.startsWith("give ")) {
            applyGive(username, cmd);
        }
    }

    private void applyClear(String username, String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length >= 2 && !isSelfSelector(parts[1], username) && !looksLikeItemToken(parts[1])) {
            return;
        }
        if (parts.length >= 3 || (parts.length == 2 && looksLikeItemToken(parts[1]))) {
            String itemTok = parts.length >= 3 ? parts[2] : parts[1];
            int nid = networkIdForItemName(itemTok);
            if (nid > 0) {
                ctx.inventory.clearItem(username, nid);
            } else {
                ctx.inventory.clear(username);
            }
        } else {
            ctx.inventory.clear(username);
        }
    }

    private void applyGive(String username, String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String item;
        int count;
        if (parts.length >= 3 && (isSelfSelector(parts[1], username) || !looksLikeItemToken(parts[1]))) {
            if (!isSelfSelector(parts[1], username) && !parts[1].equalsIgnoreCase(username)) {
                return;
            }
            item = parts[2];
            count = parts.length >= 4 ? parseIntSafe(parts[3], 1) : 1;
        } else {
            item = parts[1];
            count = parts.length >= 3 ? parseIntSafe(parts[2], 1) : 1;
        }
        int nid = networkIdForItemName(item);
        if (nid > 0) {
            ctx.inventory.give(username, nid, count);
        }
    }

    static boolean isSelfSelector(String token, String username) {
        if (token == null) {
            return false;
        }
        String t = token.toLowerCase(Locale.ROOT);
        return t.equals("@s") || t.equals("@p") || t.equalsIgnoreCase(username);
    }

    static boolean looksLikeItemToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.startsWith("@")) {
            return false;
        }
        try {
            Integer.parseInt(token);
            return false;
        } catch (NumberFormatException ignored) {
        }
        return true;
    }

    static int networkIdForItemName(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String name = raw.trim();
        if (!name.contains(":")) {
            name = "minecraft:" + name.toLowerCase(Locale.ROOT);
        } else {
            name = name.toLowerCase(Locale.ROOT);
        }
        int brace = name.indexOf('{');
        if (brace > 0) {
            name = name.substring(0, brace);
        }
        int bracket = name.indexOf('[');
        if (bracket > 0) {
            name = name.substring(0, bracket);
        }
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.name().equals(name)) {
                return s.runtimeId() & 0xFFFF;
            }
        }
        return 0;
    }

    static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
