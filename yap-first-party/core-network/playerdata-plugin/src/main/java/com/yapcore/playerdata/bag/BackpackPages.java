package com.yapcore.playerdata.bag;

import java.util.function.Predicate;

/**
 * How many bag pages a player may open.
 * {@code yapdata.bag.pages.N} means at least N pages. {@code yapdata.bag.pages.*} is the config max.
 */
public final class BackpackPages {

    public static final String NODE_USE = "yapdata.bag";
    public static final String NODE_SEE = "yapdata.bag.see";
    public static final String NODE_PAGES_ALL = "yapdata.bag.pages.*";
    public static final String NODE_PAGES_PREFIX = "yapdata.bag.pages.";

    private BackpackPages() {
    }

    public static int resolve(Predicate<String> hasPermission, int defaultPages, int maxPages) {
        int max = Math.max(1, maxPages);
        int granted = Math.min(max, Math.max(1, defaultPages));
        if (hasPermission.test(NODE_PAGES_ALL) || hasPermission.test("yapdata.admin")) {
            return max;
        }
        for (int i = max; i >= 1; i--) {
            if (hasPermission.test(NODE_PAGES_PREFIX + i)) {
                return Math.min(max, Math.max(granted, i));
            }
        }
        return granted;
    }

    public static int clampPage(int requested, int pages) {
        int max = Math.max(1, pages);
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, max);
    }
}
