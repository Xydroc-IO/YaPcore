package com.yapcore.factions;

import com.yapcore.factions.cmd.FactionCommands;
import com.yapcore.factions.cmd.YapFactionsCommand;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.db.FactionDatabase;
import com.yapcore.factions.db.FactionRepository;
import com.yapcore.factions.listener.FactionChatListener;
import com.yapcore.factions.listener.FactionDeathListener;
import com.yapcore.factions.listener.FactionPowerRegenTask;
import com.yapcore.factions.listener.FactionTerritoryListener;
import com.yapcore.factions.listener.FactionUpkeepTask;
import com.yapcore.factions.papi.FactionsPlaceholders;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionsPlugin extends JavaPlugin {

    private FactionsConfig config;
    private FactionDatabase database;
    private FactionRepository repository;
    private FactionChatState chatState;
    private FactionServiceImpl factionService;
    private FactionPowerRegenTask powerRegenTask;
    private FactionUpkeepTask upkeepTask;
    private FactionsPlaceholders placeholders;
    private FactionsPlaceholders guildPlaceholders;
    private Listener deathListener;
    private Listener territoryListener;
    private Listener chatListener;
    private boolean featuresActive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chatState = new FactionChatState();
        // Always bind admin reload so ops can enable without a full restart.
        bindCommand("yapfactions", new YapFactionsCommand(this, null));
        reloadFactions();
        applyFeatureState();
        if (!config.enabled()) {
            getLogger().info("YaPFactions disabled via config (opt-in). Set enabled: true then /yapfactions reload.");
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
            applyFeatureState();
            return;
        }
        if (repository == null) {
            repository = new FactionRepository(database);
        }

        if (factionService != null) {
            getServer().getServicesManager().unregister(FactionService.class, factionService);
        }
        factionService = new FactionServiceImpl(this, config, repository, chatState);
        applyFeatureState();
    }

    private void applyFeatureState() {
        if (config != null && config.enabled() && factionService != null) {
            activateFeatures();
        } else {
            tearDownFeatures();
            bindDisabledPlayerCommands();
            bindCommand("yapfactions", new YapFactionsCommand(this, factionService));
        }
    }

    private void activateFeatures() {
        tearDownFeatures();

        FactionCommands commands = new FactionCommands(this, config, factionService);
        bindCommand("f", commands);
        bindCommand("guild", commands);
        bindCommand("yapfactions", new YapFactionsCommand(this, factionService));

        deathListener = new FactionDeathListener(factionService);
        territoryListener = new FactionTerritoryListener(config, factionService);
        chatListener = new FactionChatListener(config, factionService);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(territoryListener, this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        powerRegenTask = new FactionPowerRegenTask(this, config, factionService);
        powerRegenTask.start();
        upkeepTask = new FactionUpkeepTask(this, config, factionService);
        upkeepTask.start();

        placeholders = new FactionsPlaceholders(factionService, "yapfaction");
        placeholders.tryRegister();
        guildPlaceholders = new FactionsPlaceholders(factionService, "yapguild");
        guildPlaceholders.tryRegister();

        getServer().getServicesManager().register(FactionService.class, factionService, this, ServicePriority.Normal);
        featuresActive = true;
        getLogger().info("YaPFactions ready (" + config.labelPlural().toLowerCase() + ")");
    }

    private void tearDownFeatures() {
        if (placeholders != null) {
            placeholders.unregisterSafe();
            placeholders = null;
        }
        if (guildPlaceholders != null) {
            guildPlaceholders.unregisterSafe();
            guildPlaceholders = null;
        }
        if (upkeepTask != null) {
            upkeepTask.stop();
            upkeepTask = null;
        }
        if (powerRegenTask != null) {
            powerRegenTask.stop();
            powerRegenTask = null;
        }
        if (deathListener != null) {
            HandlerList.unregisterAll(deathListener);
            deathListener = null;
        }
        if (territoryListener != null) {
            HandlerList.unregisterAll(territoryListener);
            territoryListener = null;
        }
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
        if (factionService != null) {
            getServer().getServicesManager().unregister(FactionService.class, factionService);
        }
        featuresActive = false;
    }

    private void bindDisabledPlayerCommands() {
        org.bukkit.command.CommandExecutor disabled = (sender, command, label, args) -> {
            sender.sendMessage("§cYaPFactions is disabled. Set §fenabled: true §cin plugins/YaPFactions/config.yml then §f/yapfactions reload§c.");
            return true;
        };
        bindCommand("f", disabled);
        bindCommand("guild", disabled);
    }

    public FactionServiceImpl factionService() {
        return factionService;
    }

    public FactionsConfig factionsConfig() {
        return config;
    }

    public boolean featuresActive() {
        return featuresActive;
    }

    public void runUpkeepNow(CommandSender sender, String factionRef) {
        if (upkeepTask == null || factionService == null) {
            sender.sendMessage("§cFeatures not active.");
            return;
        }
        int n = upkeepTask.collectNow(factionRef);
        sender.sendMessage("§aUpkeep collect ran for §f" + n + " §afaction(s).");
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
