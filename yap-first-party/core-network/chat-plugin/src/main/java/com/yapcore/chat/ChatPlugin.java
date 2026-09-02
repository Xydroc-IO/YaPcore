package com.yapcore.chat;

import com.yapcore.chat.cmd.ChatExtraCommands;
import com.yapcore.chat.cmd.MsgCommands;
import com.yapcore.chat.service.ChatFilterService;
import com.yapcore.chat.service.ChatServiceImpl;
import com.yapcore.chat.service.IgnoreService;
import com.yapcore.chat.service.PlayerChannelService;
import com.yapcore.chat.service.PrivateMessageService;
import com.yapcore.chat.service.SlowModeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Full first-party chat: unsigned fix, channels, PM, staff chat, mute/filter/slow integration.
 */
public final class ChatPlugin extends JavaPlugin {

    private ChatConfig config;
    private ChatServiceImpl chatService;
    private PrivateMessageService privateMessages;
    private SlowModeService slowMode;
    private ChatFilterService filter;
    private PlayerChannelService channels;
    private IgnoreService ignore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadChat();

        MsgCommands msgCommands = new MsgCommands(this, config, privateMessages, channels);
        ChatExtraCommands extraCommands = new ChatExtraCommands(this, config, channels, ignore);
        for (String name : new String[]{"msg", "reply", "staffchat", "adminchat", "yapchat"}) {
            bind(name, msgCommands);
        }
        for (String name : new String[]{"channel", "clearchat", "ignore", "unignore", "ignorelist"}) {
            bind(name, extraCommands);
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, ChatServiceImpl.PLUGIN_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, ChatServiceImpl.PLUGIN_CHANNEL, new ChatNetworkListener(chatService));

        getServer().getPluginManager().registerEvents(
                new ChatListener(config, slowMode, filter, channels, ignore, chatService), this);
        getServer().getPluginManager().registerEvents(new CommandMuteListener(config), this);

        getServer().getServicesManager().register(
                com.yapcore.chat.ChatService.class, chatService, this, ServicePriority.Normal);

        getLogger().info("YaPChat ready — unsigned=" + config.unsignedSystemChat()
                + " network=" + config.networkEnabled());
    }

    @Override
    public void onDisable() {
        if (chatService != null) {
            getServer().getServicesManager().unregister(com.yapcore.chat.ChatService.class, chatService);
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(exec);
            if (exec instanceof org.bukkit.command.TabCompleter completer) {
                cmd.setTabCompleter(completer);
            }
        }
    }

    public void reloadChat() {
        config = new ChatConfig(this);
        config.reload();
        if (privateMessages == null) {
            privateMessages = new PrivateMessageService();
        }
        if (slowMode == null) {
            slowMode = new SlowModeService();
        }
        if (channels == null) {
            channels = new PlayerChannelService();
        }
        if (ignore == null) {
            ignore = new IgnoreService();
        }
        filter = new ChatFilterService(config);
        chatService = new ChatServiceImpl(this, config);
    }
}
