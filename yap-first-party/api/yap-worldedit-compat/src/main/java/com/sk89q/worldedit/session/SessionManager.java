package com.sk89q.worldedit.session;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.bukkit.BukkitAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final Map<UUID, LocalSession> sessions = new ConcurrentHashMap<>();

    public LocalSession get(Player player) {
        if (player == null) {
            return new LocalSession();
        }
        org.bukkit.entity.Player bp = BukkitAdapter.adapt(player);
        UUID id = bp != null ? bp.getUniqueId() : UUID.nameUUIDFromBytes(player.getName().getBytes());
        return sessions.computeIfAbsent(id, u -> new LocalSession());
    }

    public LocalSession get(org.bukkit.entity.Player player) {
        if (player == null) {
            return new LocalSession();
        }
        return sessions.computeIfAbsent(player.getUniqueId(), u -> new LocalSession());
    }

    public LocalSession getIfPresent(UUID id) {
        return sessions.get(id);
    }

    public void remove(UUID id) {
        sessions.remove(id);
    }
}
