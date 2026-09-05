package com.yapcore.essentials.store;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffService {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean toggleFreeze(org.bukkit.entity.Player target) {
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
