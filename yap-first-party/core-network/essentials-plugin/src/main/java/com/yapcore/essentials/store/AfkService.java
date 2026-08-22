package com.yapcore.essentials.store;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkService {

    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        if (afk.contains(player.getUniqueId())) {
            afk.remove(player.getUniqueId());
            return false;
        }
        afk.add(player.getUniqueId());
        return true;
    }

    public boolean isAfk(UUID uuid) {
        return afk.contains(uuid);
    }

    public void clear(UUID uuid) {
        afk.remove(uuid);
    }
}
