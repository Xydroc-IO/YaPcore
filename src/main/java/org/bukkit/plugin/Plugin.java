package org.bukkit.plugin;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

public interface Plugin {
    File getDataFolder();

    PluginDescriptionFile getDescription();

    FileConfiguration getConfig();

    void saveConfig();

    void saveDefaultConfig();

    void reloadConfig();

    PluginLoader getPluginLoader();

    Server getServer();

    boolean isEnabled();

    void onLoad();

    void onEnable();

    void onDisable();

    boolean isNaggable();

    void setNaggable(boolean canNag);

    Logger getLogger();

    String getName();

    InputStream getResource(String filename);

    boolean onCommand(CommandSender sender, String commandLabel, String[] args);
}
