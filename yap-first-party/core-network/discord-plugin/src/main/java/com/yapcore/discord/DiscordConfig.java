package com.yapcore.discord;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordConfig {

    private final JavaPlugin plugin;
    private String moderationWebhook = "";
    private String chatWebhook = "";
    private boolean mcToDiscord;
    private boolean discordToMc;
    private boolean inboundEnabled = true;
    private int inboundPort = 8765;
    private String inboundSecret = "change-me";
    private String inboundPath = "/discord/inbound";

    public DiscordConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        moderationWebhook = c.getString("webhooks.moderation", "");
        chatWebhook = c.getString("webhooks.chat", "");
        mcToDiscord = c.getBoolean("relay.mc-to-discord", false);
        discordToMc = c.getBoolean("relay.discord-to-mc", false);
        inboundEnabled = c.getBoolean("inbound.enabled", true);
        inboundPort = Math.max(1024, c.getInt("inbound.port", 8765));
        inboundSecret = c.getString("inbound.secret", "change-me");
        inboundPath = c.getString("inbound.path", "/discord/inbound");
    }

    public String moderationWebhook() {
        return moderationWebhook;
    }

    public String chatWebhook() {
        return chatWebhook;
    }

    public boolean mcToDiscord() {
        return mcToDiscord;
    }

    public boolean discordToMc() {
        return discordToMc;
    }

    public boolean inboundEnabled() {
        return inboundEnabled;
    }

    public int inboundPort() {
        return inboundPort;
    }

    public String inboundSecret() {
        return inboundSecret;
    }

    public String inboundPath() {
        return inboundPath == null || inboundPath.isBlank() ? "/discord/inbound" : inboundPath;
    }
}
