package com.yapcore.protect.cmd;

import com.yapcore.protect.ProtectLookupCursor;
import com.yapcore.protect.util.DurationParser;

import java.util.concurrent.TimeUnit;

/** Shared duration / cursor parsing for protect command handlers. */
final class ProtectCommandArgParse {

    private ProtectCommandArgParse() {
    }

    static ProtectLookupCursor parseCursor(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--cursor".equalsIgnoreCase(args[i])) {
                return ProtectLookupCursor.decode(args[i + 1]).orElse(null);
            }
        }
        return null;
    }

    static boolean isCursorFlag(String[] args, int index) {
        return index < args.length && "--cursor".equalsIgnoreCase(args[index]);
    }

    static String formatDurationHint(long durationMs) {
        long days = TimeUnit.MILLISECONDS.toDays(durationMs);
        if (days >= 1) {
            return days + "d";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        if (hours >= 1) {
            return hours + "h";
        }
        return Math.max(1, TimeUnit.MILLISECONDS.toMinutes(durationMs)) + "m";
    }

    static long defaultDurationMs(String[] args, int durationIndex) {
        if (args.length > durationIndex && looksLikeDuration(args[durationIndex])) {
            try {
                return DurationParser.parseToMillis(args[durationIndex]);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return TimeUnit.DAYS.toMillis(7);
    }

    static boolean looksLikeDuration(String token) {
        if (token == null || token.isBlank() || "--cursor".equalsIgnoreCase(token)) {
            return false;
        }
        char last = token.charAt(token.length() - 1);
        return last == 's' || last == 'm' || last == 'h' || last == 'd' || last == 'w';
    }
}
