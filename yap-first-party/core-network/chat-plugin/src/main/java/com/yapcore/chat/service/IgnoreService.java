package com.yapcore.chat.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IgnoreService {

    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();

    public boolean toggle(Player source, Player target) {
        Set<UUID> set = ignored.computeIfAbsent(source.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (set.contains(target.getUniqueId())) {
            set.remove(target.getUniqueId());
            return false;
        }
        set.add(target.getUniqueId());
        return true;
    }

    public boolean isIgnoring(Player source, Player target) {
        Set<UUID> set = ignored.get(source.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    public Set<UUID> ignored(Player source) {
        return ignored.getOrDefault(source.getUniqueId(), Set.of());
    }
}
