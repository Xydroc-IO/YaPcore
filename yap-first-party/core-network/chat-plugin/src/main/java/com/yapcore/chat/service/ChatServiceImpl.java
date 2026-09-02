package com.yapcore.chat.service;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatFormat;
import com.yapcore.chat.ChatService;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ChatServiceImpl implements ChatService {

    public static final String PLUGIN_CHANNEL = "yap:chat";

    private final JavaPlugin plugin;
    private final ChatConfig config;

    public ChatServiceImpl(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Collection<String> channelIds() {
        return config.channels().keySet();
    }

    @Override
    public String defaultChannelId() {
        return config.defaultChannel();
    }

    @Override
    public void sendChannelMessage(String channelId, UUID senderUuid, String senderName, String plainMessage) {
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) {
            return;
        }
        Component rendered = ChatFormat.format(config, sender, plainMessage, channelId);
        broadcastLocal(channelId, rendered);
    }

    @Override
    public CompletableFuture<Void> relayNetworkMessage(String channelId, String serverId,
                                                       UUID senderUuid, String senderName,
                                                       String plainMessage) {
        return CompletableFuture.runAsync(() -> YapSched.global(plugin, () -> {
            if (serverId.equalsIgnoreCase(config.serverId())) {
                return;
            }
            Component rendered = ChatFormat.formatNetwork(
                    config, channelId, serverId, senderName, plainMessage);
            broadcastLocal(channelId, rendered);
        }));
    }

    public void forwardLocalChat(String channelId, UUID senderUuid, String senderName, String plainMessage) {
        if (!config.networkEnabled()) {
            return;
        }
        if (!config.networkRelayChannels().contains(channelId.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        String payload = "RELAY|" + channelId + "|" + config.serverId() + "|"
                + senderUuid + "|" + senderName + "|" + plainMessage.replace('|', '/');
        carrier.sendPluginMessage(plugin, PLUGIN_CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
    }

    public void handleIncomingRelay(byte[] data) {
        String payload = new String(data, StandardCharsets.UTF_8);
        if (!payload.startsWith("RELAY|")) {
            return;
        }
        String[] parts = payload.split("\\|", 6);
        if (parts.length < 6) {
            return;
        }
        String channelId = parts[1];
        String serverId = parts[2];
        UUID senderUuid;
        try {
            senderUuid = UUID.fromString(parts[3]);
        } catch (IllegalArgumentException e) {
            return;
        }
        String senderName = parts[4];
        String message = parts[5];
        relayNetworkMessage(channelId, serverId, senderUuid, senderName, message);
    }

    private void broadcastLocal(String channelId, Component rendered) {
        ChatConfig.ChannelDef channelDef = config.channel(channelId);
        int radius = channelDef.radius();
        Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!config.canUseChannel(target, channelId)) {
                continue;
            }
            if (radius > 0 && any != null && (any.getWorld() != target.getWorld()
                    || any.getLocation().distanceSquared(target.getLocation()) > radius * radius)) {
                continue;
            }
            ChatFormat.sendSystem(target, rendered);
        }
    }
}
