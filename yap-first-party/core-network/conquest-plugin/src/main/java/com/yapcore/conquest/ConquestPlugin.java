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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ConquestPlugin extends JavaPlugin {

    private ConquestConfig config;
    private ConquestDatabase database;
    private ConquestRepository repository;
    private ConquestZoneRepository zoneRepository;
    private ConquestServiceImpl conquestService;
    private ConquestFlyListener flyListener;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean featuresActive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bindCommand("yapconquest", new YapConquestCommand(this));
        reloadConquest();
        applyFeatureState();
        if (!config.enabled()) {
            getLogger().info("YaPConquest disabled via config (opt-in). Set enabled: true then /yapconquest reload.");
        }
    }

    @Override
    public void onDisable() {
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
            database = new ConquestDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPConquest database failed: " + e.getMessage());
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
            getServer().getServicesManager().unregister(ConquestService.class, conquestService);
        }
        conquestService = new ConquestServiceImpl(this, config, repository, zoneRepository);
        applyFeatureState();
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

        getServer().getServicesManager().register(
                ConquestService.class, conquestService, this, ServicePriority.Normal);
        featuresActive = true;
        getLogger().info("YaPConquest ready (" + featureSummary() + ")");
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
        getServer().getPluginManager().registerEvents(listener, this);
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
            getServer().getServicesManager().unregister(ConquestService.class, conquestService);
        }
        featuresActive = false;
    }

    private void bindDisabledPlayerCommands() {
        org.bukkit.command.CommandExecutor disabled = (sender, command, label, args) -> {
            sender.sendMessage("§cYaPConquest is disabled. Set §fenabled: true §cin plugins/YaPConquest/config.yml then §f/yapconquest reload§c.");
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
        PluginCommand cmd = getCommand(name);
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
