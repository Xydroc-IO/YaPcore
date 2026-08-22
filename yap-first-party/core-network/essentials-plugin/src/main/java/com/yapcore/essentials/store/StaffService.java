package com.yapcore.essentials.store;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffService {

    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean toggleSocialSpy(Player player) {
        if (socialSpy.contains(player.getUniqueId())) {
            socialSpy.remove(player.getUniqueId());
            return false;
        }
        socialSpy.add(player.getUniqueId());
        return true;
    }

    public boolean isSocialSpy(UUID uuid) {
        return socialSpy.contains(uuid);
    }

    public boolean toggleFreeze(Player target) {
        if (frozen.contains(target.getUniqueId())) {
            frozen.remove(target.getUniqueId());
            return false;
        }
        frozen.add(target.getUniqueId());
        return true;
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }
}
