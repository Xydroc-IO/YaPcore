package com.yapcore.playerdata.bag;

/**
 * Chest title shared with the optional {@code yap-bag} Fabric client.
 * Keep the {@code YaP Bag · page/pages} prefix stable.
 */
public final class BackpackTitle {

    public static final String PREFIX = "YaP Bag";

    private BackpackTitle() {
    }

    public static String format(int page, int pages, String extra) {
        String base = PREFIX + " · " + page + "/" + pages;
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return base + " · " + extra;
    }
}
