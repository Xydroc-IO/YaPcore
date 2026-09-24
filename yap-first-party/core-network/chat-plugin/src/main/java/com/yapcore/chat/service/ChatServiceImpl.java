package com.yapcore.chat.service;

import com.yapcore.chat.ChatConfig;
import com.yapcore.chat.ChatFormat;
import com.yapcore.chat.ChatRelayOps;
import com.yapcore.chat.ChatService;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Optional;
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

    /**
     * Build the plugin-message payload for a local chat line when network relay applies.
     * Empty when network is off, channel is not relayed, or inputs are invalid.
     */
    public Optional<byte[]> prepareRelayPayload(String channelId, UUID senderUuid,
                                                String senderName, String plainMessage) {
        if (!ChatRelayOps.shouldRelay(config, channelId) || senderUuid == null) {
            return Optional.empty();
        }
        return Optional.of(ChatRelayOps.encodeBytes(
                channelId, config.serverId(), senderUuid, senderName, plainMessage));
    }

    public void forwardLocalChat(String channelId, UUID senderUuid, String senderName, String plainMessage) {
        Optional<byte[]> payload = prepareRelayPayload(channelId, senderUuid, senderName, plainMessage);
        if (payload.isEmpty()) {
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        carrier.sendPluginMessage(plugin, PLUGIN_CHANNEL, payload.get());
    }

    public void handleIncomingRelay(byte[] data) {
        Optional<ChatRelayOps.RelayPacket> packet = ChatRelayOps.parse(data);
        if (packet.isEmpty()) {
            return;
        }
        ChatRelayOps.RelayPacket p = packet.get();
        relayNetworkMessage(p.channelId(), p.serverId(), p.senderUuid(), p.senderName(), p.message());
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
