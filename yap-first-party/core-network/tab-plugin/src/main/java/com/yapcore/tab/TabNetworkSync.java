package com.yapcore.tab;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Publishes and receives {@code yap:tab} sync packets across YaP Link backends. */
public final class TabNetworkSync implements PluginMessageListener {

    public static final String PLUGIN_CHANNEL = "yap:tab";

    private final TabPlugin plugin;
    private final TabConfig config;
    private final TabNetworkState networkState;

    public TabNetworkSync(TabPlugin plugin, TabConfig config, TabNetworkState networkState) {
        this.plugin = plugin;
        this.config = config;
        this.networkState = networkState;
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PLUGIN_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PLUGIN_CHANNEL, this);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, PLUGIN_CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, PLUGIN_CHANNEL, this);
    }

    public void publishLocalSnapshot() {
        if (!config.networkSyncEnabled()) {
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        byte[] payload = TabNetworkState.encodeSync(
                config.serverId(),
                config.header(),
                config.footer(),
                config.sidebar(),
                config.sidebarEnabled(),
                config.nametagTeams());
        carrier.sendPluginMessage(plugin, PLUGIN_CHANNEL, payload);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PLUGIN_CHANNEL.equals(channel)) {
            return;
        }
        TabNetworkState.decode(message).ifPresent(packet -> {
            if (packet.serverId().equalsIgnoreCase(config.serverId())) {
                return;
            }
            networkState.apply(
                    packet.serverId(),
                    packet.header(),
                    packet.footer(),
                    packet.sidebar(),
                    packet.sidebarEnabled(),
                    packet.nametagTeams());
            plugin.tabService().refreshAll();
        });
    }
}
