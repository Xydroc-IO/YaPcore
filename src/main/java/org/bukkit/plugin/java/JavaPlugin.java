package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for Spigot/Paper/Purpur-style plugins.
 * World mutations performed from async contexts should use
 * {@link org.bukkit.Bukkit#getScheduler()}{@code runTask} so YaPcore's
 * Compatibility Bridge can apply them safely on the game thread.
 */
public abstract class JavaPlugin implements Plugin, org.bukkit.command.CommandExecutor {

    private boolean isEnabled;
    private PluginLoader loader;
    private Server server;
    private File file;
    private PluginDescriptionFile description;
    private File dataFolder;
    private File configFile;
    private FileConfiguration config;
    private boolean naggable = true;
    private Logger logger;

    protected JavaPlugin() {
    }

    public final void init(PluginLoader loader, Server server, PluginDescriptionFile description,
                           File dataFolder, File file) {
        this.loader = loader;
        this.server = server;
        this.description = description;
        this.dataFolder = dataFolder;
        this.file = file;
        this.configFile = new File(dataFolder, "config.yml");
        this.logger = Logger.getLogger(description.getName());
    }

    @Override
    public final File getDataFolder() {
        return dataFolder;
    }

    @Override
    public final PluginDescriptionFile getDescription() {
        return description;
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save config", e);
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    @Override
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream def = getResource("config.yml");
        if (def != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(def);
            for (String key : defaults.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaults.get(key));
                }
            }
        }
    }

    public org.bukkit.command.PluginCommand getCommand(String name) {
        if (server == null || name == null) {
            return null;
        }
        return server.getPluginCommand(name);
    }

    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }
        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            return;
        }
        File outFile = new File(dataFolder, resourcePath);
        if (outFile.exists() && !replace) {
            return;
        }
        try {
            outFile.getParentFile().mkdirs();
            try (in; var out = java.nio.file.Files.newOutputStream(outFile.toPath())) {
                in.transferTo(out);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + resourcePath, e);
        }
    }

    @Override
    public PluginLoader getPluginLoader() {
        return loader;
    }

    @Override
    public final Server getServer() {
        return server;
    }

    @Override
    public final boolean isEnabled() {
        return isEnabled;
    }

    protected final void setEnabled(boolean enabled) {
        if (isEnabled == enabled) {
            return;
        }
        boolean previous = isEnabled;
        isEnabled = enabled;
        try {
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (Throwable t) {
            isEnabled = previous;
            throw t;
        }
    }

    /** Called by the YaPcore plugin loader. */
    public final void enable() {
        setEnabled(true);
    }

    public final void disable() {
        setEnabled(false);
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean isNaggable() {
        return naggable;
    }

    @Override
    public void setNaggable(boolean canNag) {
        this.naggable = canNag;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getName() {
        return description.getName();
    }

    @Override
    public InputStream getResource(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    @Override
    public boolean onCommand(CommandSender sender, String commandLabel, String[] args) {
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command,
                             String label, String[] args) {
        return onCommand(sender, label, args);
    }

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return null;
    }

    public final File getFile() {
        return file;
    }
}
