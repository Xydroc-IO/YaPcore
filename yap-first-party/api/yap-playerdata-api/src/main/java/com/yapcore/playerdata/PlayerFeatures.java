package com.yapcore.playerdata;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Player-facing QoL modules (bag, homes, warps, kits, mail, economy cmds, shops, AH, jobs,
 * claims UX, /menu) owned by <strong>YaPEssentials</strong> and backed by YaPPlayerData storage.
 * Essentials binds these from its own {@code plugin.yml} via {@link PlayerFeaturesProvider}.
 */
public interface PlayerFeatures {

    /** Command names Essentials should wire (may be disabled stubs). */
    Set<String> commandNames();

    @Nullable
    CommandExecutor executor(String commandName);

    @Nullable
    TabCompleter tabCompleter(String commandName);

    /** Gameplay listeners — register on Essentials. */
    List<Listener> listeners();

    /** Optional host hook (e.g. bag plugin channels). */
    default void onHostEnable(JavaPlugin host) {
    }

    default void onHostDisable(JavaPlugin host) {
    }
}
