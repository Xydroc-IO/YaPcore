package com.yapcore.chat;

import com.yapcore.chat.service.ChatServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ChatNetworkListener implements PluginMessageListener {

    private final ChatServiceImpl chatService;

    public ChatNetworkListener(ChatServiceImpl chatService) {
        this.chatService = chatService;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ChatServiceImpl.PLUGIN_CHANNEL.equals(channel)) {
            return;
        }
        chatService.handleIncomingRelay(message);
    }
}
