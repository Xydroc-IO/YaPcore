package com.yapcore.bedrockblocks;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric client channel {@code yap:blocks} — HELLO for Phase 4 parity clients.
 */
public final class BlocksChannel implements PluginMessageListener {

    public static final String CHANNEL = "yap:blocks";

    private final JavaPlugin plugin;
    private final Set<UUID> clients = ConcurrentHashMap.newKeySet();

    public BlocksChannel(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        try {
            messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
        clients.clear();
    }

    public boolean hasHello(UUID uuid) {
        return uuid != null && clients.contains(uuid);
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            clients.remove(uuid);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null || message == null) {
            return;
        }
        String text = new String(message, StandardCharsets.UTF_8).trim();
        if (text.regionMatches(true, 0, "HELLO", 0, 5)) {
            clients.add(player.getUniqueId());
            plugin.getLogger().fine(() -> "yap:blocks HELLO from " + player.getName());
        }
    }
}
