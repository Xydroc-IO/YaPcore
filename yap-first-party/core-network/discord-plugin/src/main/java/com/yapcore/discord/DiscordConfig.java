package com.yapcore.discord;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordConfig {

    private final JavaPlugin plugin;
    private String moderationWebhook = "";
    private String chatWebhook = "";
    private String eventsWebhook = "";
    private boolean mcToDiscord;
    private boolean discordToMc;
    private boolean eventJoin;
    private boolean eventLeave;
    private boolean eventDeath;
    private boolean eventAdvancement;
    private boolean inboundEnabled;
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
        eventsWebhook = c.getString("webhooks.events", "");
        mcToDiscord = c.getBoolean("relay.mc-to-discord", false);
        discordToMc = c.getBoolean("relay.discord-to-mc", false);
        eventJoin = c.getBoolean("events.join", false);
        eventLeave = c.getBoolean("events.leave", false);
        eventDeath = c.getBoolean("events.death", false);
        eventAdvancement = c.getBoolean("events.advancement", false);
        inboundEnabled = c.getBoolean("inbound.enabled", false);
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

    public String eventsWebhook() {
        return eventsWebhook == null || eventsWebhook.isBlank() ? chatWebhook : eventsWebhook;
    }

    public boolean mcToDiscord() {
        return mcToDiscord;
    }

    public boolean discordToMc() {
        return discordToMc;
    }

    public boolean eventJoin() {
        return eventJoin;
    }

    public boolean eventLeave() {
        return eventLeave;
    }

    public boolean eventDeath() {
        return eventDeath;
    }

    public boolean eventAdvancement() {
        return eventAdvancement;
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
