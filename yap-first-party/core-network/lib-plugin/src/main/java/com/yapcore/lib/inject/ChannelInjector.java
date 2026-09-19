package com.yapcore.lib.inject;

import com.yapcore.lib.YapLibConfig;
import com.yapcore.lib.dispatch.PacketDispatcher;
import io.netty.channel.Channel;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.util.logging.Level;

/** Paper ChannelInitializeListenerHolder — inject on every new connection. */
public final class ChannelInjector {

    private static final Key LISTENER_KEY = Key.key("yaplib", "packets");

    private final JavaPlugin plugin;
    private final ConnectionTracker tracker;
    private final PacketDispatcher dispatcher;
    private final YapLibConfig config;
    private boolean installed;

    public ChannelInjector(JavaPlugin plugin, ConnectionTracker tracker, PacketDispatcher dispatcher,
                           YapLibConfig config) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.dispatcher = dispatcher;
        this.config = config;
    }

    public void install() {
        if (installed) {
            return;
        }
        ClassLoader serverCl = plugin.getServer().getClass().getClassLoader();
        try {
            Class<?> holder = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListenerHolder", true, serverCl);
            Class<?> listener = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListener", true, serverCl);
            Object proxy = Proxy.newProxyInstance(serverCl, new Class<?>[]{listener}, (p, m, args) -> {
                if ("afterInitChannel".equals(m.getName()) && args != null && args.length == 1) {
                    inject((Channel) args[0]);
                }
                return null;
            });
            holder.getMethod("addListener", Key.class, listener).invoke(null, LISTENER_KEY, proxy);
            installed = true;
            plugin.getLogger().info("YaPLib pipeline hook installed (before packet_handler)");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not hook ChannelInitializeListenerHolder", e);
        }
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        ClassLoader serverCl = plugin.getServer().getClass().getClassLoader();
        try {
            Class<?> holder = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListenerHolder", true, serverCl);
            holder.getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
        } catch (ReflectiveOperationException ignored) {
            // shutting down
        }
        for (Channel channel : tracker.snapshot().keySet()) {
            PacketChannelHandler.eject(channel);
        }
        installed = false;
    }

    public void injectPlayer(Player player) {
        Channel channel = ChannelAccess.channel(player);
        if (channel != null) {
            tracker.bind(player, channel);
            inject(channel);
        }
    }

    public void inject(Channel channel) {
        PacketChannelHandler.inject(channel, new PacketChannelHandler(tracker, dispatcher, config));
    }

    public boolean installed() {
        return installed;
    }
}
