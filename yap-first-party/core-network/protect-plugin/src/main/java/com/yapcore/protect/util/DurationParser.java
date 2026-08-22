package com.yapcore.protect.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code 30m}, {@code 2h}, {@code 7d} into milliseconds. */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static long parseToMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("empty duration");
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return TimeUnit.HOURS.toMillis(Long.parseLong(trimmed));
        }
        long total = 0L;
        Matcher m = TOKEN.matcher(trimmed);
        boolean found = false;
        while (m.find()) {
            found = true;
            long amount = Long.parseLong(m.group(1));
            total += switch (m.group(2).charAt(0)) {
                case 's' -> TimeUnit.SECONDS.toMillis(amount);
                case 'm' -> TimeUnit.MINUTES.toMillis(amount);
                case 'h' -> TimeUnit.HOURS.toMillis(amount);
                case 'd' -> TimeUnit.DAYS.toMillis(amount);
                case 'w' -> TimeUnit.DAYS.toMillis(amount * 7);
                default -> throw new IllegalArgumentException("bad unit in " + input);
            };
        }
        if (!found) {
            throw new IllegalArgumentException("bad duration: " + input);
        }
        return total;
    }
}
