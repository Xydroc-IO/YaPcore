package me.clip.placeholderapi.util;

import java.util.concurrent.TimeUnit;

/** Time formatting helper some expansions import. */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String getRemaining(int seconds, TimeUnit type) {
        return switch (type) {
            case DAYS -> String.valueOf(TimeUnit.SECONDS.toDays(seconds));
            case HOURS -> String.valueOf(TimeUnit.SECONDS.toHours(seconds));
            case MINUTES -> String.valueOf(TimeUnit.SECONDS.toMinutes(seconds));
            default -> String.valueOf(seconds);
        };
    }

    public static String getTime(int seconds) {
        int days = seconds / 86400;
        seconds %= 86400;
        int hours = seconds / 3600;
        seconds %= 3600;
        int minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append('d').append(' ');
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append('h').append(' ');
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append('m').append(' ');
        }
        sb.append(seconds).append('s');
        return sb.toString().trim();
    }
}
