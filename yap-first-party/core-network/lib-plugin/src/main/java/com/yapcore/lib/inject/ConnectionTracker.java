package com.yapcore.lib.inject;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Channel ↔ player bind for handshake-through-play. */
public final class ConnectionTracker {

    private final Map<Channel, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    public void bind(Player player, Channel channel) {
        if (player == null || channel == null) {
            return;
        }
        Channel previous = channels.put(player.getUniqueId(), channel);
        if (previous != null && previous != channel) {
            players.remove(previous, player);
        }
        players.put(channel, player);
    }

    public void unbind(Player player) {
        if (player == null) {
            return;
        }
        Channel channel = channels.remove(player.getUniqueId());
        if (channel != null) {
            players.remove(channel, player);
        }
    }

    public void unbind(Channel channel) {
        if (channel == null) {
            return;
        }
        Player player = players.remove(channel);
        if (player != null) {
            channels.remove(player.getUniqueId(), channel);
        }
    }

    public Player player(Channel channel) {
        return channel == null ? null : players.get(channel);
    }

    public Channel channel(UUID playerId) {
        return playerId == null ? null : channels.get(playerId);
    }

    public Channel channel(Player player) {
        if (player == null) {
            return null;
        }
        Channel mapped = channels.get(player.getUniqueId());
        if (mapped != null) {
            return mapped;
        }
        return ChannelAccess.channel(player);
    }

    public Map<Channel, Player> snapshot() {
        return Map.copyOf(players);
    }

    public int size() {
        return players.size();
    }

    public void clear() {
        players.clear();
        channels.clear();
    }
}
