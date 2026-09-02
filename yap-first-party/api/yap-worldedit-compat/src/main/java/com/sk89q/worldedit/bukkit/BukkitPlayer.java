package com.sk89q.worldedit.bukkit;

import com.sk89q.worldedit.entity.Player;

public final class BukkitPlayer implements Player {

    private final org.bukkit.entity.Player player;

    public BukkitPlayer(org.bukkit.entity.Player player) {
        this.player = player;
    }

    public org.bukkit.entity.Player getPlayer() {
        return player;
    }

    @Override
    public String getName() {
        return player.getName();
    }
}
