package com.yapcore.chat.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMessageService {

    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public void sent(Player from, Player to) {
        lastPartner.put(from.getUniqueId(), to.getUniqueId());
        lastPartner.put(to.getUniqueId(), from.getUniqueId());
    }

    public Player replyTarget(Player sender) {
        UUID partnerId = lastPartner.get(sender.getUniqueId());
        if (partnerId == null) {
            return null;
        }
        return sender.getServer().getPlayer(partnerId);
    }
}
