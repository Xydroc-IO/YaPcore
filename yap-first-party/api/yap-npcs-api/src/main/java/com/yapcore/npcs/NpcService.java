package com.yapcore.npcs;

import org.bukkit.entity.Player;

import java.util.List;

/** Persistent quest NPCs with dialogue and quest turn-in hooks. */
public interface NpcService {

    boolean create(Player player, String id, String displayName);

    boolean remove(String id);

    List<String> listIds();

    void respawnAll();
}
