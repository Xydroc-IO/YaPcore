package com.yapcore.conquest;

import com.yapcore.conquest.cmd.ConquestCommands;
import com.yapcore.conquest.cmd.YapConquestCommand;
import com.yapcore.conquest.db.ConquestDatabase;
import com.yapcore.conquest.db.ConquestRepository;
import com.yapcore.conquest.db.ConquestZoneRepository;
import com.yapcore.conquest.listener.ConquestCombatListener;
import com.yapcore.conquest.listener.ConquestFlyListener;
import com.yapcore.conquest.listener.ConquestTerritoryListener;
import com.yapcore.conquest.service.ConquestServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Chunk conquest hosted by YaPFactions. Off unless {@code conquest.yml} {@code enabled} is true.
 */
public final class ConquestPlugin {

    private final JavaPlugin host;
    private File configFile;
    private FileConfiguration fileConfig;
    private ConquestConfig config;
    private ConquestDatabase database;
    private ConquestRepository repository;
    private ConquestZoneRepository zoneRepository;
    private ConquestServiceImpl conquestService;
    private ConquestFlyListener flyListener;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean featuresActive;

    public ConquestPlugin(JavaPlugin host) {
        this.host = host;
    }

    public JavaPlugin bukkit() {
        return host;
    }

    public void enable() {
        saveDefaultConfig();
        bindCommand("yapconquest", new YapConquestCommand(this));
        reloadConquest();
        if (config != null && !config.enabled()) {
            host.getLogger().info("YaPConquest disabled (conquest.yml enabled: false). "
                    + "Set enabled: true then /yapconquest reload.");
        }
    }

    public void disable() {
        tearDownFeatures();
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public void reloadConquest() {
        if (config == null) {
            config = new ConquestConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new ConquestDatabase(host, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            host.getLogger().severe("YaPConquest database failed: " + e.getMessage());
            conquestService = null;
            applyFeatureState();
            return;
        }
        if (repository == null) {
            repository = new ConquestRepository(database);
        }
        if (zoneRepository == null) {
            zoneRepository = new ConquestZoneRepository(database);
        }

        if (conquestService != null) {
            host.getServer().getServicesManager().unregister(ConquestService.class, conquestService);
        }
        conquestService = new ConquestServiceImpl(host, config, repository, zoneRepository);
        applyFeatureState();
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public FileConfiguration getConfig() {
        return fileConfig;
    }

    public void saveDefaultConfig() {
        if (!host.getDataFolder().isDirectory() && !host.getDataFolder().mkdirs()) {
            host.getLogger().warning("Could not create " + host.getDataFolder());
        }
        configFile = new File(host.getDataFolder(), "conquest.yml");
        if (!configFile.isFile()) {
            File legacy = new File(host.getDataFolder().getParentFile(), "YaPConquest/config.yml");
            if (legacy.isFile()) {
                try {
                    Files.copy(legacy.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    host.getLogger().info("Migrated " + legacy + " → " + configFile);
                } catch (IOException e) {
                    host.getLogger().warning("Could not migrate conquest config: " + e.getMessage());
                }
            }
            if (!configFile.isFile() && host.getResource("conquest.yml") != null) {
                host.saveResource("conquest.yml", false);
            }
        }
        reloadConfig();
    }

    public void reloadConfig() {
        if (configFile == null) {
            saveDefaultConfig();
            return;
        }
        fileConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    private void applyFeatureState() {
        if (config != null && config.enabled() && conquestService != null) {
            activateFeatures();
        } else {
            tearDownFeatures();
            bindDisabledPlayerCommands();
            bindCommand("yapconquest", new YapConquestCommand(this));
        }
    }

    private void activateFeatures() {
        tearDownFeatures();

        ConquestCommands commands = new ConquestCommands(this, config, conquestService);
        bindCommand("c", commands);
        bindCommand("yapconquest", new YapConquestCommand(this));

        flyListener = new ConquestFlyListener(config, conquestService);
        register(new ConquestTerritoryListener(config, conquestService));
        register(flyListener);
        register(new ConquestCombatListener(config, conquestService, flyListener));

        host.getServer().getServicesManager().register(
                ConquestService.class, conquestService, host, ServicePriority.Normal);
        featuresActive = true;
        host.getLogger().info("YaPConquest ready (" + featureSummary() + ")");
    }

    private String featureSummary() {
        List<String> bits = new ArrayList<>();
        bits.add("chunk land");
        if (config.zonesEnabled()) {
            bits.add("zones");
        }
        if (config.overclaimEnabled()) {
            bits.add("overclaim");
        }
        if (config.explosionsEnabled()) {
            bits.add("explosions");
        }
        if (config.flyEnabled()) {
            bits.add("fly");
        }
        if (config.combatTagEnabled()) {
            bits.add("combat-tag");
        }
        return String.join(", ", bits);
    }

    private void register(Listener listener) {
        host.getServer().getPluginManager().registerEvents(listener, host);
        listeners.add(listener);
    }

    private void tearDownFeatures() {
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        if (flyListener != null) {
            flyListener.clearAll();
            flyListener = null;
        }
        if (conquestService != null) {
            host.getServer().getServicesManager().unregister(ConquestService.class, conquestService);
        }
        featuresActive = false;
    }

    private void bindDisabledPlayerCommands() {
        org.bukkit.command.CommandExecutor disabled = (sender, command, label, args) -> {
            sender.sendMessage("§cYaPConquest is disabled. Set §fenabled: true §cin plugins/YaPFactions/conquest.yml then §f/yapconquest reload§c.");
            return true;
        };
        bindCommand("c", disabled);
    }

    public boolean featuresActive() {
        return featuresActive;
    }

    public ConquestServiceImpl conquestService() {
        return conquestService;
    }

    public ConquestConfig conquestConfig() {
        return config;
    }

    private void bindCommand(String name, Object executor) {
        PluginCommand cmd = host.getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        } else {
            cmd.setTabCompleter(null);
        }
    }
}
