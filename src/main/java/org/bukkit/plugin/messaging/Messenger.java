package org.bukkit.plugin.messaging;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Plugin messaging messenger (Velocity / Bungee style channels). */
public interface Messenger {

    void registerOutgoingPluginChannel(Plugin plugin, String channel);

    void registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener);

    void unregisterIncomingPluginChannel(Plugin plugin, String channel);

    void unregisterOutgoingPluginChannel(Plugin plugin, String channel);

    Set<String> getIncomingChannels();

    Set<String> getOutgoingChannels();

    void dispatchIncoming(String channel, Player player, byte[] data);
}
