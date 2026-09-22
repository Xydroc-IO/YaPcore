package com.yapcore.yap420.channel;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Plugin channel {@code yap:420} for optional Fabric haze clients. */
public final class HazeChannel implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final String channel;
    private final Set<UUID> helloClients = ConcurrentHashMap.newKeySet();

    public HazeChannel(JavaPlugin plugin, Yap420Config config) {
        this.plugin = plugin;
        this.channel = config.hazeChannel();
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, channel);
        messenger.registerIncomingPluginChannel(plugin, channel, this);
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        try {
            messenger.unregisterIncomingPluginChannel(plugin, channel, this);
        } catch (Exception ignored) {
        }
        try {
            messenger.unregisterOutgoingPluginChannel(plugin, channel);
        } catch (Exception ignored) {
        }
        helloClients.clear();
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            helloClients.remove(uuid);
        }
    }

    public boolean hasHello(UUID uuid) {
        return uuid != null && helloClients.contains(uuid);
    }

    public void sendHaze(Player player, double intensity, int durationTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        byte[] payload = HazePayload.encodeHaze(intensity, durationTicks);
        YapSched.entity(plugin, player, () -> {
            try {
                // Always send — vanilla ignores unknown channels; Fabric clients apply FX.
                player.sendPluginMessage(plugin, channel, payload);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "haze send failed", e);
            }
        });
    }

    @Override
    public void onPluginMessageReceived(String ch, Player player, byte[] message) {
        if (!channel.equals(ch) || player == null) {
            return;
        }
        if (HazePayload.isHello(message)) {
            helloClients.add(player.getUniqueId());
            YapSched.entity(plugin, player, () -> {
                try {
                    player.sendPluginMessage(plugin, channel, HazePayload.encodeHelloAck());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "haze hello ack failed", e);
                }
            });
        }
    }
}
