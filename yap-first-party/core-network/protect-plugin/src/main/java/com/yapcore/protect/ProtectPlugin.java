package com.yapcore.protect;

import com.yapcore.protect.cmd.ProtectCommands;
import com.yapcore.protect.listener.BlockChangeListener;
import com.yapcore.protect.listener.ContainerInventoryListener;
import com.yapcore.protect.listener.ContainerAccessListener;
import com.yapcore.protect.listener.EntityChangeListener;
import com.yapcore.protect.service.ProtectServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class ProtectPlugin extends JavaPlugin {

    private ProtectConfig config;
    private ProtectServiceImpl service;
    private ProtectCommands commands;
    private boolean listenersRegistered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new ProtectConfig(this);
        service = new ProtectServiceImpl(this);
        commands = new ProtectCommands(service, config);
        try {
            config.reload();
            service.start(config);
        } catch (SQLException e) {
            getLogger().severe("YaPProtect DB failed — disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!listenersRegistered) {
            var pm = getServer().getPluginManager();
            pm.registerEvents(new BlockChangeListener(service), this);
            pm.registerEvents(new ContainerAccessListener(service), this);
            pm.registerEvents(new ContainerInventoryListener(service), this);
            pm.registerEvents(new EntityChangeListener(service), this);
            listenersRegistered = true;
        }

        getServer().getServicesManager().register(ProtectService.class, service, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yapprotect");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getLogger().info("YaPProtect ready — logging + rollback (server-id=" + config.serverId() + ").");
    }

    public void reloadProtect() throws SQLException {
        config.reload();
        service.reload(config);
        commands.setConfig(config);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(ProtectService.class, service);
        service.shutdown();
    }
}
