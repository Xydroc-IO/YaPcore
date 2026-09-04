package com.yapcore.commands;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** YaPCommands — YAML custom /commands with dashboard CRUD. */
public final class CommandsPlugin extends JavaPlugin {

    private CommandRegistry registry;
    private boolean featureEnabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("commands.yml", false);
        registry = new CommandRegistry(this);
        featureEnabled = getConfig().getBoolean("enabled", true);
        PluginCommand admin = getCommand("yapcommands");
        registry.bindAdmin(admin);
        if (featureEnabled) {
            registry.reload();
        } else {
            getLogger().info("YaPCommands disabled in config.yml (enabled: false)");
        }
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.unregisterAll();
        }
    }

    public CommandRegistry registry() {
        return registry;
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    public boolean requireUsePerm() {
        return registry != null && registry.requireUsePerm();
    }

    public void reloadAll() {
        reloadConfig();
        featureEnabled = getConfig().getBoolean("enabled", true);
        if (registry == null) {
            registry = new CommandRegistry(this);
            registry.bindAdmin(getCommand("yapcommands"));
        }
        if (featureEnabled) {
            registry.reload();
        } else {
            registry.unregisterAll();
            getLogger().info("YaPCommands disabled — all custom commands unregistered");
        }
    }
}
