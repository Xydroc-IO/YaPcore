package com.yapcore.guard;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuardPlugin extends JavaPlugin {

    private GuardConfig config;
    private ViolationTracker tracker;
    private GuardServiceImpl service;
    private GuardListener listener;
    private GuardCommands commands;
    private boolean libHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGuard();

        listener = new GuardListener(this, config, tracker, service);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.startMovementChecks();
        libHooked = hookLib();
        if (libHooked) {
            listener.setPacketSpeed(true);
            listener.setPacketReach(true);
            listener.setPacketScaffold(true);
        }

        getServer().getServicesManager().register(GuardService.class, service, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yapguard");
        if (cmd != null) {
            commands = new GuardCommands(this, config, tracker, service);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("YaPGuard ready — lightweight heuristics (not Grim). "
                + "PvP: ./scripts/grim-ac.sh enable · maxViolations=" + config.maxViolationsBeforeKick()
                + " packets=" + libHooked
                + " sampleRandomly=" + config.sampleRandomly());
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

    public void flag(Player player, String check) {
        if (listener != null) {
            listener.flag(player, check);
        }
    }

    public ViolationTracker tracker() {
        return tracker;
    }

    public GuardServiceImpl guardService() {
        return service;
    }

    @Override
    public void onDisable() {
        if (libHooked) {
            unhookLib();
            libHooked = false;
        }
        if (service != null) {
            getServer().getServicesManager().unregister(GuardService.class, service);
        }
        if (listener != null) {
            listener.stopMovementChecks();
        }
    }

    private boolean hookLib() {
        if (getServer().getPluginManager().getPlugin("YaPLib") == null) {
            return false;
        }
        try {
            Object ok = Class.forName("com.yapcore.guard.GuardLibHook")
                    .getMethod("install", GuardPlugin.class)
                    .invoke(null, this);
            return Boolean.TRUE.equals(ok);
        } catch (ReflectiveOperationException e) {
            getLogger().warning("YaPLib present but guard packet hook failed: " + e.getMessage());
            return false;
        }
    }

    private void unhookLib() {
        try {
            Class.forName("com.yapcore.guard.GuardLibHook")
                    .getMethod("uninstall", org.bukkit.plugin.Plugin.class)
                    .invoke(null, this);
        } catch (ReflectiveOperationException ignored) {
            // YaPLib already gone
        }
    }
}
