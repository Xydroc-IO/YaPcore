package com.yapcore.qol;

import com.yapcore.items.item.ItemFactory;
import com.yapcore.qol.gui.QolAdminGui;
import com.yapcore.qol.listener.ExcavatorListener;
import com.yapcore.qol.listener.TimberListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Timber axe and excavator tools hosted by YaPItems.
 * Item stacks come from {@code items/tools.yml}; knobs live in {@code qol.yml}.
 */
public final class QolPlugin {

    private final JavaPlugin plugin;
    private final ItemFactory factory;
    private File configFile;
    private FileConfiguration fileConfig;
    private QolConfig config;
    private QolKeys keys;
    private QolItems items;
    private BlockBreakHelper breaks;
    private QolAdminGui adminGui;

    public QolPlugin(JavaPlugin plugin, ItemFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    public JavaPlugin bukkit() {
        return plugin;
    }

    public void enable() {
        loadConfigFile();
        config = new QolConfig(this);
        config.reload();
        keys = new QolKeys();
        items = new QolItems(config, keys, factory);
        breaks = new BlockBreakHelper(plugin);
        adminGui = new QolAdminGui(this);

        var events = plugin.getServer().getPluginManager();
        events.registerEvents(new TimberListener(config, items, breaks), plugin);
        events.registerEvents(new ExcavatorListener(config, items, breaks), plugin);
        events.registerEvents(adminGui, plugin);

        QolCommands commands = new QolCommands(this, config, items, adminGui);
        PluginCommand cmd = plugin.getCommand("yapqol");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        plugin.getLogger().info("YaPItems tools — timber=" + config.timberEnabled()
                + " excavator=" + config.excavatorEnabled()
                + " sizes=" + config.excavatorSizes());
    }

    public void reloadQol() {
        reloadConfig();
        if (config != null) {
            config.reload();
        }
    }

    public QolConfig qolConfig() {
        return config;
    }

    public QolItems items() {
        return items;
    }

    public QolAdminGui adminGui() {
        return adminGui;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public FileConfiguration getConfig() {
        return fileConfig;
    }

    public void reloadConfig() {
        if (configFile == null) {
            loadConfigFile();
            return;
        }
        fileConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        if (fileConfig == null || configFile == null) {
            return;
        }
        try {
            fileConfig.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + configFile + ": " + e.getMessage());
        }
    }

    private void loadConfigFile() {
        if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create " + plugin.getDataFolder());
        }
        configFile = new File(plugin.getDataFolder(), "qol.yml");
        if (!configFile.isFile()) {
            File legacy = new File(plugin.getDataFolder().getParentFile(), "YaP-QoL/config.yml");
            if (legacy.isFile()) {
                try {
                    Files.copy(legacy.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Migrated " + legacy + " → " + configFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not migrate QoL config: " + e.getMessage());
                }
            }
            if (!configFile.isFile() && plugin.getResource("qol.yml") != null) {
                plugin.saveResource("qol.yml", false);
            }
        }
        fileConfig = YamlConfiguration.loadConfiguration(configFile);
    }
}
