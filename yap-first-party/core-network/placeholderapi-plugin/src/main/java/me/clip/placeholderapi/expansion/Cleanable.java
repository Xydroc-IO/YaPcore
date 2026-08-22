package me.clip.placeholderapi.expansion;

import org.bukkit.entity.Player;

/** Expansion notified when a player quits. */
public interface Cleanable {

    void cleanup(Player player);
}
