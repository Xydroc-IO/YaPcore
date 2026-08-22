package com.yapcore.guard;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuardPlugin extends JavaPlugin {

    private GuardConfig config;
    private ViolationTracker tracker;
    private GuardServiceImpl service;
    private GuardListener listener;
    private GuardCommands commands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGuard();

        listener = new GuardListener(this, config, tracker, service);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.startMovementChecks();

        getServer().getServicesManager().register(GuardService.class, service, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yapguard");
        if (cmd != null) {
            commands = new GuardCommands(this, config, tracker, service);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("YaPGuard ready — max violations before kick=" + config.maxViolationsBeforeKick());
    }

    public void reloadGuard() {
        if (config == null) {
            config = new GuardConfig(this);
        }
        config.reload();
        if (tracker == null) {
            tracker = new ViolationTracker(config);
        } else {
            tracker.setConfig(config);
        }
        service = new GuardServiceImpl(tracker);
        if (commands != null) {
            commands.setConfig(config);
        }
        if (listener != null) {
            listener.setConfig(config);
        }
    }

    public GuardConfig guardConfig() {
        return config;
    }

    public ViolationTracker tracker() {
        return tracker;
    }

    public GuardServiceImpl guardService() {
        return service;
    }

    @Override
    public void onDisable() {
        if (service != null) {
            getServer().getServicesManager().unregister(GuardService.class, service);
        }
        if (listener != null) {
            listener.stopMovementChecks();
        }
    }
}
