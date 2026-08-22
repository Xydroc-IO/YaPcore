package com.yapcore.moderation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like 30m, 2h, 7d, 1w. */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static long parseToEpochMs(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Empty duration");
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("forever")) {
            return 0L;
        }
        Matcher matcher = TOKEN.matcher(trimmed);
        long totalMs = 0L;
        int lastEnd = 0;
        while (matcher.find()) {
            lastEnd = matcher.end();
            long amount = Long.parseLong(matcher.group(1));
            totalMs += amount * unitMs(matcher.group(2));
        }
        if (totalMs <= 0L || lastEnd != trimmed.length()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return System.currentTimeMillis() + totalMs;
    }

    private static long unitMs(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            default -> throw new IllegalArgumentException("Unknown unit: " + unit);
        };
    }

    public static String formatExpiry(long expiresAtEpochMs) {
        if (expiresAtEpochMs <= 0L) {
            return "Permanent";
        }
        long remaining = expiresAtEpochMs - System.currentTimeMillis();
        if (remaining <= 0L) {
            return "Expired";
        }
        long seconds = remaining / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) {
            return days + "d " + (hours % 24L) + "h";
        }
        if (hours > 0) {
            return hours + "h " + (minutes % 60L) + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60L) + "s";
        }
        return seconds + "s";
    }
}
