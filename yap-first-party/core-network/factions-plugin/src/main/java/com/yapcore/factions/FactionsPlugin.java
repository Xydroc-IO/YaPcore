package com.yapcore.factions;

import com.yapcore.factions.FactionService;
import com.yapcore.factions.cmd.FactionCommands;
import com.yapcore.factions.cmd.YapFactionsCommand;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.db.FactionDatabase;
import com.yapcore.factions.db.FactionRepository;
import com.yapcore.factions.listener.FactionDeathListener;
import com.yapcore.factions.listener.FactionPowerRegenTask;
import com.yapcore.factions.listener.FactionTerritoryListener;
import com.yapcore.factions.papi.FactionsPlaceholders;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionsPlugin extends JavaPlugin {

    private FactionsConfig config;
    private FactionDatabase database;
    private FactionRepository repository;
    private FactionChatState chatState;
    private FactionServiceImpl factionService;
    private FactionPowerRegenTask powerRegenTask;
    private FactionsPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chatState = new FactionChatState();
        reloadFactions();

        if (!config.enabled()) {
            getLogger().info("YaPFactions disabled via config.");
            return;
        }
        if (factionService == null) {
            getLogger().severe("YaPFactions failed to start — database not ready.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bindCommand("f", new FactionCommands(this, config, factionService));
        bindCommand("yapfactions", new YapFactionsCommand(this, factionService));

        getServer().getPluginManager().registerEvents(new FactionDeathListener(factionService), this);
        getServer().getPluginManager().registerEvents(new FactionTerritoryListener(config, factionService), this);

        powerRegenTask = new FactionPowerRegenTask(this, config, factionService);
        powerRegenTask.start();

        placeholders = new FactionsPlaceholders(factionService);
        placeholders.tryRegister();

        getServer().getServicesManager().register(FactionService.class, factionService, this, ServicePriority.Normal);
        getLogger().info("YaPFactions ready");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregisterSafe();
        }
        if (factionService != null) {
            getServer().getServicesManager().unregister(FactionService.class, factionService);
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadFactions() {
        if (config == null) {
            config = new FactionsConfig(this);
        }
        config.reload();

        if (chatState == null) {
            chatState = new FactionChatState();
        }

        if (database == null) {
            database = new FactionDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPFactions database failed: " + e.getMessage());
            factionService = null;
            return;
        }
        if (repository == null) {
            repository = new FactionRepository(database);
        }

        if (placeholders != null) {
            placeholders.unregisterSafe();
        }

        var sm = getServer().getServicesManager();
        if (factionService != null) {
            sm.unregister(FactionService.class, factionService);
        }
        factionService = new FactionServiceImpl(this, config, repository, chatState);

        if (isEnabled()) {
            placeholders = new FactionsPlaceholders(factionService);
            placeholders.tryRegister();
            if (powerRegenTask != null) {
                powerRegenTask.start();
            }
        }
    }

    public FactionServiceImpl factionService() {
        return factionService;
    }

    private void bindCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }
}
