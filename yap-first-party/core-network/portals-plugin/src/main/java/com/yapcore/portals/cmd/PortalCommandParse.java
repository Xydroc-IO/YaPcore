package com.yapcore.portals.cmd;

import org.bukkit.command.CommandSender;

final class PortalCommandParse {

    private PortalCommandParse() {
    }

    static int indexOf(String[] args, String token, int from) {
        for (int i = Math.max(0, from); i < args.length; i++) {
            if (token.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    static int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNot a number: §f" + raw);
            return Integer.MIN_VALUE;
        }
    }

    static String[] copyFrom(String[] args, int from) {
        if (from >= args.length) {
            return new String[0];
        }
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    /** Link server id; {@code hub} is an alias for {@code lobby}. */
    static String resolveTarget(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("hub".equals(t)) {
            return "lobby";
        }
        return t;
    }
}
