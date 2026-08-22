package com.yapcore.factions.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionChatState {

    public enum Channel {
        PUBLIC,
        FACTION,
        ALLY
    }

    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    public Channel channel(UUID playerId) {
        return channels.getOrDefault(playerId, Channel.PUBLIC);
    }

    public void setChannel(UUID playerId, Channel channel) {
        if (channel == Channel.PUBLIC) {
            channels.remove(playerId);
        } else {
            channels.put(playerId, channel);
        }
    }

    public void clear(UUID playerId) {
        channels.remove(playerId);
    }
}
