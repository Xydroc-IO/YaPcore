package com.yapcore.claims;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publishes {@code /claim} + shovel listeners for YaPEssentials. */
public final class ClaimFeaturesImpl implements ClaimFeatures {

    private final Map<String, CommandExecutor> executors = new LinkedHashMap<>();
    private final Map<String, TabCompleter> tabs = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    void put(String name, CommandExecutor exec, TabCompleter tab) {
        executors.put(name, exec);
        if (tab != null) {
            tabs.put(name, tab);
        }
    }

    void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public Set<String> commandNames() {
        return Set.copyOf(executors.keySet());
    }

    @Override
    public @Nullable CommandExecutor executor(String commandName) {
        return executors.get(commandName);
    }

    @Override
    public @Nullable TabCompleter tabCompleter(String commandName) {
        return tabs.get(commandName);
    }

    @Override
    public List<Listener> listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public void onHostDisable(JavaPlugin host) {
        // no-op
    }
}
