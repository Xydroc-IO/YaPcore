package org.bukkit.plugin.messaging;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface PluginMessageListener {
    void onPluginMessageReceived(String channel, Player player, byte[] message);
}
