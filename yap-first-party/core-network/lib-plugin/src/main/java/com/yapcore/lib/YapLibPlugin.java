package com.yapcore.lib;

import com.yapcore.lib.cmd.YapLibCommands;
import com.yapcore.lib.dispatch.ListenerRegistry;
import com.yapcore.lib.dispatch.PacketDispatcher;
import com.yapcore.lib.dispatch.PacketServiceImpl;
import com.yapcore.lib.inject.ChannelInjector;
import com.yapcore.lib.inject.ConnectionTracker;
import com.yapcore.lib.nms.PacketAllocator;
import com.yapcore.lib.inject.PacketSendOps;
import com.yapcore.lib.inject.PlayerBindListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class YapLibPlugin extends JavaPlugin {

    private YapLibConfig config;
    private ConnectionTracker tracker;
    private ListenerRegistry registry;
    private PacketServiceImpl packetService;
    private ChannelInjector injector;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        config = new YapLibConfig(this);
        config.reload();
        tracker = new ConnectionTracker();
        registry = new ListenerRegistry();
        PacketSendOps sendOps = new PacketSendOps(tracker);
        packetService = new PacketServiceImpl(registry, tracker, sendOps, new PacketAllocator(this));
        getServer().getServicesManager().register(PacketService.class, packetService, this, ServicePriority.Highest);
    }

    @Override
    public void onEnable() {
        if (config == null) {
            onLoad();
        }
        PacketDispatcher dispatcher = new PacketDispatcher(this, config, registry);
        injector = new ChannelInjector(this, tracker, dispatcher, config);
        injector.install();
        getServer().getPluginManager().registerEvents(new PlayerBindListener(injector, tracker, registry), this);
        for (var player : Bukkit.getOnlinePlayers()) {
            injector.injectPlayer(player);
        }

        PluginCommand lib = getCommand("yaplib");
        if (lib != null) {
            YapLibCommands commands = new YapLibCommands(this);
            lib.setExecutor(commands);
            lib.setTabCompleter(commands);
        }
        getLogger().info("YaPLib ready — intercept=" + config.intercept()
                + " listeners=" + packetService.listenerCount()
                + " (holograms: yap-holo.jar)");
    }

    public void reloadLib() {
        config.reload();
    }

    public YapLibConfig libConfig() {
        return config;
    }

    public PacketServiceImpl packetService() {
        return packetService;
    }

    public ConnectionTracker tracker() {
        return tracker;
    }

    @Override
    public void onDisable() {
        if (injector != null) {
            injector.uninstall();
        }
        if (packetService != null) {
            getServer().getServicesManager().unregister(PacketService.class, packetService);
        }
        if (registry != null) {
            registry.clear();
        }
        if (tracker != null) {
            tracker.clear();
        }
    }
}
