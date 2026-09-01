package com.yapcore.world.web;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived tokens linking a browser session to an in-game player. */
public final class WorldEditSessionRegistry {

    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, Entry> byToken = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokenByPlayer = new ConcurrentHashMap<>();

    public String openSession(UUID playerId, String playerName) {
        revoke(playerId);
        String token = randomToken();
        byToken.put(token, new Entry(playerId, playerName, Instant.now().toEpochMilli()));
        tokenByPlayer.put(playerId, token);
        return token;
    }

    public Optional<Entry> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = byToken.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().toEpochMilli() - entry.createdMs() > 30 * 60 * 1000L) {
            revoke(entry.playerId());
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void revoke(UUID playerId) {
        String old = tokenByPlayer.remove(playerId);
        if (old != null) {
            byToken.remove(old);
        }
    }

    private static String randomToken() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(48);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record Entry(UUID playerId, String playerName, long createdMs) {
    }
}
