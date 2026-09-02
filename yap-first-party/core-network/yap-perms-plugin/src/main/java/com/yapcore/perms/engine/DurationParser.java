package com.yapcore.perms.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LuckPerms-style duration tokens: {@code 30s}, {@code 5m}, {@code 2h}, {@code 1d}, {@code 1w}, {@code 1d12h}. */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*(mo|w|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static boolean looksLike(String raw) {
        return parse(raw).isPresent();
    }

    public static boolean isPermanent(String raw) {
        if (raw == null) {
            return true;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() || t.equals("0") || t.equals("perm") || t.equals("permanent")
                || t.equals("forever") || t.equals("*");
    }

    public static Optional<Duration> parse(String raw) {
        if (isPermanent(raw)) {
            return Optional.empty();
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN.matcher(t);
        long seconds = 0;
        int consumed = 0;
        while (matcher.find()) {
            consumed += matcher.group().length();
            long n = Long.parseLong(matcher.group(1));
            seconds += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "mo" -> n * 30L * 86400L;
                case "w" -> n * 7L * 86400L;
                case "d" -> n * 86400L;
                case "h" -> n * 3600L;
                case "m" -> n * 60L;
                case "s" -> n;
                default -> 0;
            };
        }
        String stripped = t.replaceAll("\\s+", "");
        if (seconds <= 0 || consumed == 0 || stripped.length() > consumed + 2) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    public static Optional<Instant> expiryFromNow(String raw) {
        return parse(raw).map(d -> Instant.now().plus(d));
    }

    public static String format(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        long seconds = duration.getSeconds();
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append('d');
        }
        if (h > 0) {
            sb.append(h).append('h');
        }
        if (m > 0) {
            sb.append(m).append('m');
        }
        if (s > 0 || sb.isEmpty()) {
            sb.append(s).append('s');
        }
        return sb.toString();
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) {
            return "ready";
        }
        return format(Duration.ofSeconds(seconds));
    }
}
