package com.yapcore.protect;

import java.util.Optional;

/**
 * Stable keyset cursor for paginated protect lookups ({@code epochMs} DESC, {@code id} DESC).
 * Wire token: {@code epochMs:id}.
 */
public record ProtectLookupCursor(long epochMs, long id) {

    public String encode() {
        return epochMs + ":" + id;
    }

    public static Optional<ProtectLookupCursor> decode(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int sep = token.indexOf(':');
        if (sep <= 0 || sep >= token.length() - 1) {
            return Optional.empty();
        }
        try {
            long epoch = Long.parseLong(token.substring(0, sep));
            long id = Long.parseLong(token.substring(sep + 1));
            return Optional.of(new ProtectLookupCursor(epoch, id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
