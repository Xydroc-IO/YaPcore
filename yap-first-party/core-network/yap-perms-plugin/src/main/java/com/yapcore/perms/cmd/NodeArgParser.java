package com.yapcore.perms.cmd;

import com.yapcore.perms.engine.DurationParser;

import java.time.Instant;
import java.util.Locale;

/** Trailing LuckPerms-style node modifiers: duration, {@code world=}, {@code server=}. */
final class NodeArgParser {

    record Context(Instant expires, String world, String server) {
    }

    private NodeArgParser() {
    }

    static Context parse(String[] args, int start) {
        Instant expires = null;
        String world = "";
        String server = "";
        for (int i = start; i < args.length; i++) {
            String token = args[i];
            if (token.regionMatches(true, 0, "world=", 0, 6)) {
                world = token.substring(6);
                continue;
            }
            if (token.regionMatches(true, 0, "server=", 0, 7)) {
                server = token.substring(7);
                continue;
            }
            if (DurationParser.looksLike(token) || DurationParser.isPermanent(token)) {
                expires = DurationParser.expiryFromNow(token).orElse(null);
                continue;
            }
            // Bare world name (common LP muscle memory after duration)
            if (world.isEmpty() && !token.contains("=")) {
                world = token;
            }
        }
        return new Context(expires, world, server);
    }

    static String describe(Context ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.expires() != null) {
            sb.append(" §7until §f").append(ctx.expires());
        }
        if (!ctx.world().isEmpty()) {
            sb.append(" §7world=§f").append(ctx.world());
        }
        if (!ctx.server().isEmpty()) {
            sb.append(" §7server=§f").append(ctx.server());
        }
        return sb.toString();
    }

    static String lower(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }
}
