package com.yapcore.claims;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Claim UX ({@code /claim} + shovel listeners) owned by <strong>YaPEssentials</strong>
 * and backed by YaPClaims. Essentials binds these via {@link ClaimFeaturesProvider}.
 */
public interface ClaimFeatures {

    /** Command names Essentials should wire (typically {@code claim}). */
    Set<String> commandNames();

    @Nullable
    CommandExecutor executor(String commandName);

    @Nullable
    TabCompleter tabCompleter(String commandName);

    /** Gameplay listeners — register on Essentials (or Claims host). */
    List<Listener> listeners();

    default void onHostEnable(JavaPlugin host) {
    }

    default void onHostDisable(JavaPlugin host) {
    }
}
