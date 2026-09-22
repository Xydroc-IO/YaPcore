package com.yapcore.link.bedrock.session;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers a BungeeCord {@code Connect} target so the next Bedrock login (after
 * {@link org.cloudburstmc.protocol.bedrock.packet.TransferPacket}) joins that Folia backend.
 */
public final class BedrockConnectPending {

    private static final long TTL_MS = 60_000L;
    private static final Map<String, Entry> BY_USER = new ConcurrentHashMap<>();

    private BedrockConnectPending() {
    }

    public static void put(String username, String serverName) {
        if (username == null || username.isBlank() || serverName == null || serverName.isBlank()) {
            return;
        }
        BY_USER.put(username.trim().toLowerCase(Locale.ROOT),
                new Entry(serverName.trim(), System.currentTimeMillis() + TTL_MS));
    }

    /** Consume a pending target (one-shot). */
    public static String take(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Entry e = BY_USER.remove(username.trim().toLowerCase(Locale.ROOT));
        if (e == null || e.expiresAtMs < System.currentTimeMillis()) {
            return null;
        }
        return e.serverName;
    }

    /** Peek without consuming (for name resolution before {@link #take}). */
    public static String peek(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Entry e = BY_USER.get(username.trim().toLowerCase(Locale.ROOT));
        if (e == null || e.expiresAtMs < System.currentTimeMillis()) {
            return null;
        }
        return e.serverName;
    }

    private record Entry(String serverName, long expiresAtMs) {
    }
}
