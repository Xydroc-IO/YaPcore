package com.yapcore.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, timestamped network/GUI event produced by the Traffic Cop.
 * Never mutated after construction — safe to hand off across thread boundaries.
 */
public final class GameEvent {

    public enum Type {
        PLAYER_MOVE,
        PLAYER_CHAT,
        GUI_CLICK,
        COMMAND,
        HEARTBEAT,
        STORE_PURCHASE_REQUEST,
        CLIENT_JOIN,
        CLIENT_LEAVE,
        CLIENT_REJECTED,
        RESOURCE_PACK_OFFER,
        RESOURCE_PACK_STATUS
    }

    private final UUID eventId;
    private final Type type;
    private final String playerName;
    private final long timestampNanos;
    private final Map<String, String> payload;

    public GameEvent(Type type, String playerName, Map<String, String> payload) {
        this.eventId = UUID.randomUUID();
        this.type = Objects.requireNonNull(type, "type");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.timestampNanos = System.nanoTime();
        this.payload = Collections.unmodifiableMap(
                Map.copyOf(Objects.requireNonNull(payload, "payload")));
    }

    public UUID getEventId() {
        return eventId;
    }

    public Type getType() {
        return type;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public String payload(String key) {
        return payload.get(key);
    }

    @Override
    public String toString() {
        return "GameEvent{id=" + eventId + ", type=" + type
                + ", player=" + playerName + ", payload=" + payload + "}";
    }
}
