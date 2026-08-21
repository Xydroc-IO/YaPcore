package com.yapcore.crossplay.bedrock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Bedrock gameplay session registry (Geyser parity 4.G1+).
 * Full RakNet reliability + BE packet codecs plug in here.
 */
public final class BedrockSessionManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockSessions");

    public record BedrockSession(
            long guid,
            String username,
            int protocol,
            String address,
            long createdAtMs
    ) {
    }

    private final ConcurrentHashMap<Long, BedrockSession> byGuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byName = new ConcurrentHashMap<>();
    private final AtomicLong joins = new AtomicLong();

    public BedrockSession open(long guid, String username, int protocol, String address) {
        BedrockSession s = new BedrockSession(guid, username, protocol, address, System.currentTimeMillis());
        byGuid.put(guid, s);
        byName.put(username.toLowerCase(), guid);
        joins.incrementAndGet();
        LOG.info("Bedrock session open " + username + " proto=" + protocol + " guid=" + Long.toHexString(guid));
        return s;
    }

    public void close(long guid) {
        BedrockSession s = byGuid.remove(guid);
        if (s != null) {
            byName.remove(s.username().toLowerCase());
            LOG.info("Bedrock session close " + s.username());
        }
    }

    public BedrockSession get(long guid) {
        return byGuid.get(guid);
    }

    public BedrockSession byUsername(String username) {
        Long g = byName.get(username.toLowerCase());
        return g == null ? null : byGuid.get(g);
    }

    public int size() {
        return byGuid.size();
    }

    public long joinCount() {
        return joins.get();
    }

    public Map<Long, BedrockSession> snapshot() {
        return Map.copyOf(byGuid);
    }

    public java.util.Set<Long> allGuids() {
        return java.util.Set.copyOf(byGuid.keySet());
    }
}
