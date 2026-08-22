package com.yapcore.tab;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TabPlugin extends JavaPlugin {

    private TabConfig config;
    private TabNetworkState networkState;
    private TabServiceImpl tabService;
    private TabNetworkSync networkSync;
    private TabListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        networkState = new TabNetworkState();
        reloadTab();

        PluginCommand cmd = getCommand("yaptab");
        if (cmd != null) {
            cmd.setExecutor(new TabCommands(this));
        }

        listener = new TabListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new TabSkillListener(this), this);
        listener.startRefreshTask(config);

        networkSync = new TabNetworkSync(this, config, networkState);
        networkSync.register();
        networkSync.publishLocalSnapshot();

        getLogger().info("YaPTab ready — sidebar=" + config.sidebarEnabled()
                + " networkSync=" + config.networkSyncEnabled()
                + " bossBar=" + config.bossBarEnabled()
                + " refresh=" + config.refreshSeconds() + "s");
    }

    @Override
    public void onDisable() {
        if (networkSync != null) {
            networkSync.unregister();
        }
        if (tabService != null) {
            getServer().getServicesManager().unregister(com.yapcore.tab.TabService.class, tabService);
        }
    }

    public void reloadTab() {
        if (tabService != null) {
            getServer().getServicesManager().unregister(com.yapcore.tab.TabService.class, tabService);
        }
        if (config == null) {
            config = new TabConfig(this);
        }
        config.reload();
        tabService = new TabServiceImpl(config, networkState);
        getServer().getServicesManager().register(
                com.yapcore.tab.TabService.class, tabService, this, ServicePriority.Normal);
        if (networkSync != null) {
            networkSync.publishLocalSnapshot();
        }
    }

    public TabConfig tabConfig() {
        return config;
    }

    public TabServiceImpl tabService() {
        return tabService;
    }
}
