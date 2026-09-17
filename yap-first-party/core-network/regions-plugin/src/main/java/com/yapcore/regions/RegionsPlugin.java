package com.yapcore.regions;

import com.yapcore.regions.cmd.RegionCommands;
import com.yapcore.regions.db.AdminRegionRepository;
import com.yapcore.regions.db.RegionMessageRepository;
import com.yapcore.regions.db.RegionTemplateRepository;
import com.yapcore.regions.db.RegionsDatabase;
import com.yapcore.regions.listener.RegionGamemodeListener;
import com.yapcore.regions.listener.RegionListener;
import com.yapcore.regions.listener.RegionWorldFlagsListener;
import com.yapcore.regions.service.RegionServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionsPlugin extends JavaPlugin {

    private RegionsConfig config;
    private RegionsDatabase database;
    private AdminRegionRepository repository;
    private RegionMessageRepository messages;
    private RegionTemplateRepository templates;
    private RegionServiceImpl regionService;
    private RegionGamemodeListener gamemodeListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadRegions();

        getServer().getPluginManager().registerEvents(new RegionListener(config, regionService), this);
        getServer().getPluginManager().registerEvents(new RegionWorldFlagsListener(regionService), this);
        gamemodeListener = new RegionGamemodeListener(this, regionService);
        getServer().getPluginManager().registerEvents(gamemodeListener, this);

        PluginCommand cmd = getCommand("region");
        if (cmd != null) {
            RegionCommands commands = new RegionCommands(this, regionService);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getServer().getServicesManager().register(
                RegionService.class, regionService, this, ServicePriority.Normal);

        getLogger().info("YaPRegions ready — server=" + config.serverId()
                + " regions=" + regionService.listRegions().size());
    }

    @Override
    public void onDisable() {
        if (regionService != null) {
            getServer().getServicesManager().unregister(RegionService.class, regionService);
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadRegions() {
        if (config == null) {
            config = new RegionsConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new RegionsDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPRegions database failed: " + e.getMessage());
            return;
        }

        if (repository == null) {
            repository = new AdminRegionRepository(database);
        }
        if (messages == null) {
            messages = new RegionMessageRepository(database);
        }
        if (templates == null) {
            templates = new RegionTemplateRepository(database);
        }
        if (regionService == null) {
            regionService = new RegionServiceImpl(config, repository, messages, templates);
        }
        regionService.reload();
        if (gamemodeListener != null) {
            gamemodeListener.refreshOnlinePlayers();
        }
    }

    public RegionsConfig config() {
        return config;
    }

    public RegionGamemodeListener gamemodeListener() {
        return gamemodeListener;
    }
}
