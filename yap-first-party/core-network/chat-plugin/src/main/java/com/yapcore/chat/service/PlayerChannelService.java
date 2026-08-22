package com.yapcore.chat.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerChannelService {

    private final Map<UUID, String> channels = new ConcurrentHashMap<>();

    public String channel(Player player, String defaultChannel) {
        return channels.getOrDefault(player.getUniqueId(), defaultChannel);
    }

    public void setChannel(Player player, String channel) {
        channels.put(player.getUniqueId(), channel.toLowerCase());
    }
}
