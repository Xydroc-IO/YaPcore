package com.yapcore.bag;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Must stay in lockstep with YaPPlayerData {@code BackpackTitle}. */
public final class BagTitle {

    public static final String PREFIX = "YaP Bag";
    private static final Pattern PAGE = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    public record State(int page, int pages) {
    }

    private BagTitle() {
    }

    public static Optional<State> parse(String title) {
        if (title == null || !title.startsWith(PREFIX)) {
            return Optional.empty();
        }
        Matcher matcher = PAGE.matcher(title);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int page = Integer.parseInt(matcher.group(1));
        int pages = Integer.parseInt(matcher.group(2));
        if (page < 1 || pages < 1) {
            return Optional.empty();
        }
        return Optional.of(new State(page, pages));
    }
}
