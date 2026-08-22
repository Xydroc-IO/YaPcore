package me.clip.placeholderapi.expansion;

import org.bukkit.entity.Player;

/**
 * Relational placeholders ({@code %rel_id_params%}).
 * Clean-room clip-compatible interface.
 */
public interface Relational {

    String onPlaceholderRequest(Player one, Player two, String identifier);
}
