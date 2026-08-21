package com.yapcore.client;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks online Java and Bedrock sessions.
 */
public final class ClientRegistry {

    private final ConcurrentHashMap<UUID, ClientSession> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> byName = new ConcurrentHashMap<>();

    public ClientSession register(ClientSession session) {
        byId.put(session.getSessionId(), session);
        byName.put(session.getUsername().toLowerCase(), session);
        return session;
    }

    public void unregister(UUID sessionId) {
        ClientSession removed = byId.remove(sessionId);
        if (removed != null) {
            byName.remove(removed.getUsername().toLowerCase(), removed);
        }
    }

    public void unregister(ClientSession session) {
        unregister(session.getSessionId());
    }

    public Optional<ClientSession> get(String username) {
        return Optional.ofNullable(byName.get(username.toLowerCase()));
    }

    public Optional<ClientSession> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<ClientSession> all() {
        return byId.values();
    }

    public long countEdition(ClientEdition edition) {
        return byId.values().stream().filter(s -> s.getEdition() == edition).count();
    }

    public int size() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
        byName.clear();
    }
}
