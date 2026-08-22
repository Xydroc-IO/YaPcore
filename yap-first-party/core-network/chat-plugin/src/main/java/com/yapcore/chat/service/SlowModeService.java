package com.yapcore.chat.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SlowModeService {

    private final Map<UUID, Long> lastMessageMs = new ConcurrentHashMap<>();

    public boolean allow(Player player, int slowModeSeconds) {
        if (slowModeSeconds <= 0 || player.hasPermission("yapchat.bypass.slow")) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessageMs.get(player.getUniqueId());
        if (last != null && now - last < slowModeSeconds * 1000L) {
            return false;
        }
        lastMessageMs.put(player.getUniqueId(), now);
        return true;
    }

    public long remainingSeconds(Player player, int slowModeSeconds) {
        Long last = lastMessageMs.get(player.getUniqueId());
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        long need = slowModeSeconds * 1000L;
        if (elapsed >= need) {
            return 0L;
        }
        return (need - elapsed + 999L) / 1000L;
    }
}
