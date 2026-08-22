package com.yapcore.moderation;

import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.moderation.cmd.ModerationCommands;
import com.yapcore.moderation.db.ModerationDatabase;
import com.yapcore.moderation.db.ModerationRepository;
import com.yapcore.moderation.listener.LoginListener;
import com.yapcore.moderation.task.ExpirationTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModerationPlugin extends JavaPlugin {

    private ModerationConfig config;
    private ModerationDatabase database;
    private ModerationRepository repository;
    private AltRepository alts;
    private ModerationServiceImpl service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new ModerationConfig(this);
        config.reload();

        database = new ModerationDatabase(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Failed to open DB — disabling YaPModeration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new ModerationRepository(database);
        alts = new AltRepository(database);
        try {
            alts.migrate();
        } catch (Exception e) {
            getLogger().warning("Alt table migrate failed: " + e.getMessage());
        }
        service = new ModerationServiceImpl(repository);
        getServer().getServicesManager().register(ModerationService.class, service, this, ServicePriority.Normal);

        ModerationCommands commands = new ModerationCommands(this, service, repository, alts, config);
        for (String name : new String[]{
                "ban", "tempban", "unban", "ipban", "unbanip",
                "mute", "tempmute", "unmute", "warn", "kick",
                "modhistory", "modcheck", "banlist", "yapmod"
        }) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        getServer().getPluginManager().registerEvents(new LoginListener(service, config, alts), this);
        new ExpirationTask(this, service).start();
        getLogger().info("YaPModeration ready.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (database != null) {
            database.close();
        }
    }

    public void reloadPlugin() {
        config.reload();
        service.reload();
    }

    public ModerationServiceImpl service() {
        return service;
    }
}
