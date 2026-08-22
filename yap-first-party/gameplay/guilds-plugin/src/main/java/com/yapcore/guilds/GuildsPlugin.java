package com.yapcore.guilds;

import com.yapcore.guilds.GuildService;
import com.yapcore.guilds.cmd.GuildCommands;
import com.yapcore.guilds.cmd.YapGuildsCommand;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.guilds.db.GuildDatabase;
import com.yapcore.guilds.db.GuildRepository;
import com.yapcore.guilds.listener.GuildOnlineXpTask;
import com.yapcore.guilds.listener.GuildXpListener;
import com.yapcore.guilds.papi.GuildsPlaceholders;
import com.yapcore.guilds.service.GuildServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuildsPlugin extends JavaPlugin {

    private GuildsConfig config;
    private GuildDatabase database;
    private GuildRepository repository;
    private GuildChatState chatState;
    private GuildServiceImpl guildService;
    private GuildOnlineXpTask onlineXpTask;
    private GuildsPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chatState = new GuildChatState();
        reloadGuilds();

        if (!config.enabled()) {
            getLogger().info("YaPGuilds disabled via config.");
            return;
        }
        if (guildService == null) {
            getLogger().severe("YaPGuilds failed to start — database not ready.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GuildCommands commands = new GuildCommands(this, config, guildService);
        bindCommand("g", commands);
        bindCommand("guild", commands);
        bindCommand("yapguilds", new YapGuildsCommand(this, guildService));

        getServer().getPluginManager().registerEvents(new GuildXpListener(this, config, guildService), this);

        onlineXpTask = new GuildOnlineXpTask(this, config, guildService);
        onlineXpTask.start();

        placeholders = new GuildsPlaceholders(guildService);
        placeholders.tryRegister();

        getServer().getServicesManager().register(GuildService.class, guildService, this, ServicePriority.Normal);
        getLogger().info("YaPGuilds ready");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregisterSafe();
        }
        if (guildService != null) {
            getServer().getServicesManager().unregister(GuildService.class, guildService);
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadGuilds() {
        if (config == null) {
            config = new GuildsConfig(this);
        }
        config.reload();

        if (chatState == null) {
            chatState = new GuildChatState();
        }

        if (database == null) {
            database = new GuildDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPGuilds database failed: " + e.getMessage());
            guildService = null;
            return;
        }
        if (repository == null) {
            repository = new GuildRepository(database);
        }

        if (placeholders != null) {
            placeholders.unregisterSafe();
        }

        var sm = getServer().getServicesManager();
        if (guildService != null) {
            sm.unregister(GuildService.class, guildService);
        }
        guildService = new GuildServiceImpl(this, config, repository, chatState);

        if (isEnabled()) {
            placeholders = new GuildsPlaceholders(guildService);
            placeholders.tryRegister();
            if (onlineXpTask != null) {
                onlineXpTask.start();
            }
        }
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
