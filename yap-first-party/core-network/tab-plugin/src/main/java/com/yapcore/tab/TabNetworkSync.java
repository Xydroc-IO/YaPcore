package com.yapcore.tab;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
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
    private YapTask heartbeatTask;

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
        stopHeartbeat();
    }

    public void startHeartbeat() {
        stopHeartbeat();
        if (!config.networkSyncEnabled()) {
            return;
        }
        long ticks = Math.max(20L, config.networkSyncHeartbeatSeconds() * 20L);
        heartbeatTask = YapSched.globalTimer(plugin, this::publishLocalSnapshot, ticks, ticks);
    }

    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    public void publishLocalSnapshot() {
        if (!config.networkSyncEnabled()) {
            return;
        }
        Player carrier = firstOnlinePlayer();
        if (carrier == null) {
            return;
        }
        byte[] payload = TabNetworkState.encodeSync(
                config.serverId(),
                config.header(),
                config.footer(),
                config.sidebar(),
                config.sidebarEnabled(),
                config.nametagTeams());
        carrier.sendPluginMessage(plugin, PLUGIN_CHANNEL, payload);
    }

    public void publishClear() {
        if (!config.networkSyncEnabled()) {
            return;
        }
        Player carrier = firstOnlinePlayer();
        if (carrier == null) {
            return;
        }
        carrier.sendPluginMessage(plugin, PLUGIN_CHANNEL, TabNetworkState.encodeClear(config.serverId()));
    }

    private static Player firstOnlinePlayer() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return null;
        }
        return Bukkit.getOnlinePlayers().iterator().next();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PLUGIN_CHANNEL.equals(channel)) {
            return;
        }
        TabNetworkState.decodeClear(message).ifPresent(sourceId -> {
            if (sourceId.equalsIgnoreCase(config.serverId())) {
                return;
            }
            networkState.clear();
            plugin.tabService().refreshAll();
        });
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
