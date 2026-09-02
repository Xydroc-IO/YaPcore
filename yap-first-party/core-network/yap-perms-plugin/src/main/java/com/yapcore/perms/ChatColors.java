package com.yapcore.perms;

/** Legacy {@code &} color codes for rank name / chat colors. */
public final class ChatColors {
    private ChatColors() {
    }

    /**
     * Accepts {@code a}, {@code &a}, {@code §a}, or {@code &#rrggbb}.
     * Empty input stays empty (callers apply a default).
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace('§', '&');
        if (value.isEmpty() || "none".equalsIgnoreCase(value) || "reset".equalsIgnoreCase(value)) {
            return "";
        }
        if (value.charAt(0) != '&') {
            value = "&" + value;
        }
        return value;
    }

    public static String orDefault(String raw, String fallback) {
        String value = normalize(raw);
        return value.isEmpty() ? fallback : value;
    }
}
