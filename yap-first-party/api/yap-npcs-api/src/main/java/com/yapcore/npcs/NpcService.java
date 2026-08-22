package com.yapcore.npcs;

import org.bukkit.entity.Player;

import java.util.List;

/** Persistent quest NPCs with dialogue and quest turn-in hooks. */
public interface NpcService {

    boolean create(Player player, String id, String displayName);

    /** Console / dashboard placement at explicit coordinates. */
    boolean createAt(String id, String displayName, String world, double x, double y, double z, float yaw);

    boolean remove(String id);

    boolean setQuestId(String id, String questId);

    boolean setDialogue(String id, String dialogue);

    List<String> listIds();

    void respawnAll();

    void reloadConfig();
}
