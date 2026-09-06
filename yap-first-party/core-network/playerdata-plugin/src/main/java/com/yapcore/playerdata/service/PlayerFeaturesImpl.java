package com.yapcore.playerdata.service;

import com.yapcore.playerdata.PlayerFeatures;
import com.yapcore.playerdata.bag.BackpackChannel;
import com.yapcore.playerdata.bag.BackpackService;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects player-facing command executors + listeners built by YaPPlayerData
 * for YaPEssentials to register.
 */
public final class PlayerFeaturesImpl implements PlayerFeatures {

    private final JavaPlugin dataPlugin;
    private final Map<String, CommandExecutor> executors = new LinkedHashMap<>();
    private final Map<String, TabCompleter> tabs = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private BackpackService backpack;

    public PlayerFeaturesImpl(JavaPlugin dataPlugin) {
        this.dataPlugin = dataPlugin;
    }

    public void put(String name, CommandExecutor exec, TabCompleter tab) {
        executors.put(name, exec);
        tabs.put(name, tab);
    }

    public void putDisabled(String name, String configKey) {
        CommandExecutor disabled = (sender, command, label, args) -> {
            tellDisabled(sender, configKey);
            return true;
        };
        TabCompleter empty = (sender, command, alias, args) -> List.of();
        put(name, disabled, empty);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void bindBackpack(BackpackService backpack) {
        this.backpack = backpack;
    }

    @Override
    public Set<String> commandNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(executors.keySet()));
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
        return Collections.unmodifiableList(listeners);
    }

    @Override
    public void onHostEnable(JavaPlugin host) {
        if (backpack == null) {
            return;
        }
        // Channels stay on the data plugin (BackpackService / client expect YaPPlayerData).
        dataPlugin.getServer().getMessenger().registerOutgoingPluginChannel(dataPlugin, BackpackService.CHANNEL);
        dataPlugin.getServer().getMessenger().registerIncomingPluginChannel(
                dataPlugin, BackpackService.CHANNEL, new BackpackChannel(dataPlugin, backpack));
    }

    @Override
    public void onHostDisable(JavaPlugin host) {
        if (backpack == null) {
            return;
        }
        try {
            dataPlugin.getServer().getMessenger()
                    .unregisterIncomingPluginChannel(dataPlugin, BackpackService.CHANNEL);
            dataPlugin.getServer().getMessenger()
                    .unregisterOutgoingPluginChannel(dataPlugin, BackpackService.CHANNEL);
        } catch (Throwable ignored) {
        }
    }

    private static void tellDisabled(CommandSender sender, String configKey) {
        sender.sendMessage("§cYaPPlayerData: that feature is disabled (§f"
                + configKey + "§c in plugins/YaPPlayerData/config.yml).");
    }
}
