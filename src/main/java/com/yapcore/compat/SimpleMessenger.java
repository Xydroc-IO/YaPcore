package com.yapcore.compat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

public final class SimpleMessenger implements Messenger {

    private static final Logger LOG = Logger.getLogger("YaPcore.Messenger");

    private final Set<String> outgoing = new CopyOnWriteArraySet<>();
    private final Map<String, CopyOnWriteArraySet<PluginMessageListener>> incoming = new ConcurrentHashMap<>();

    @Override
    public void registerOutgoingPluginChannel(Plugin plugin, String channel) {
        outgoing.add(channel);
        LOG.info("Outgoing channel " + channel + " (" + plugin.getName() + ")");
    }

    @Override
    public void registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        incoming.computeIfAbsent(channel, c -> new CopyOnWriteArraySet<>()).add(listener);
        LOG.info("Incoming channel " + channel + " (" + plugin.getName() + ")");
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
        incoming.remove(channel);
    }

    @Override
    public void unregisterOutgoingPluginChannel(Plugin plugin, String channel) {
        outgoing.remove(channel);
    }

    @Override
    public Set<String> getIncomingChannels() {
        return Set.copyOf(incoming.keySet());
    }

    @Override
    public Set<String> getOutgoingChannels() {
        return Set.copyOf(outgoing);
    }

    @Override
    public void dispatchIncoming(String channel, Player player, byte[] data) {
        CopyOnWriteArraySet<PluginMessageListener> set = incoming.get(channel);
        if (set == null) {
            return;
        }
        for (PluginMessageListener l : set) {
            try {
                l.onPluginMessageReceived(channel, player, data);
            } catch (Throwable t) {
                LOG.warning("Plugin message listener failed on " + channel + ": " + t.getMessage());
            }
        }
    }
}
