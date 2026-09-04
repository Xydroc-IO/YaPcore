package com.yapcore.discord;

import com.yapcore.moderation.ModerationAudit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordPlugin extends JavaPlugin {

    private DiscordConfig config;
    private WebhookClient webhooks;
    private ModAuditBridge modBridge;
    private ChatRelayListener chatListener;
    private EventRelayListener eventListener;
    private DiscordMcRelay mcRelay;
    private DiscordInboundServer inboundServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDiscord();

        PluginCommand cmd = getCommand("yapdiscord");
        if (cmd != null) {
            cmd.setExecutor(new DiscordCommands(this));
        }

        getLogger().info("YaPDiscord ready — mod webhook="
                + (!config.moderationWebhook().isBlank())
                + " chat relay=" + config.mcToDiscord()
                + " discord→mc=" + config.discordToMc());
    }

    @Override
    public void onDisable() {
        if (inboundServer != null) {
            inboundServer.stop();
        }
        if (modBridge != null) {
            ModerationAudit.unregister(modBridge);
            modBridge = null;
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ModAuditBridge.MOD_CHANNEL);
    }

    public void reloadDiscord() {
        if (modBridge != null) {
            ModerationAudit.unregister(modBridge);
        }
        if (config == null) {
            config = new DiscordConfig(this);
        }
        config.reload();
        if (webhooks == null) {
            webhooks = new WebhookClient(this);
        }
        if (mcRelay == null) {
            mcRelay = new DiscordMcRelay(this);
        }
        if (inboundServer == null) {
            inboundServer = new DiscordInboundServer(this);
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModAuditBridge.MOD_CHANNEL);
        modBridge = new ModAuditBridge(this);
        ModerationAudit.register(modBridge);
        if (chatListener == null) {
            chatListener = new ChatRelayListener(this);
            getServer().getPluginManager().registerEvents(chatListener, this);
        }
        if (eventListener == null) {
            eventListener = new EventRelayListener(this);
            getServer().getPluginManager().registerEvents(eventListener, this);
        }
        inboundServer.start(config);
    }

    public DiscordConfig config() {
        return config;
    }

    public WebhookClient webhooks() {
        return webhooks;
    }

    public DiscordMcRelay mcRelay() {
        return mcRelay;
    }
}
