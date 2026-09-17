package com.yapcore.portals;

import org.bukkit.configuration.file.FileConfiguration;

/** Loaded from plugins/YaPPortals/config.yml. */
public final class PortalsConfig {

    private String serverId = "lobby";
    private boolean enabled = true;
    private int defaultCooldownSeconds = 3;
    private String defaultPermission = "";
    private String defaultColor = PortalColors.DEFAULT;
    private String msgTransferring = "§7Sending you to §f{server}§7…";
    private String msgDenied = "§cYou cannot use this portal.";
    private String msgCooldown = "§cWait §f{seconds}s §cbefore using another portal.";
    private String msgAlreadyHere = "§7You are already on §f{server}§7.";
    private String msgNoProxy = "§cTransfer failed — join through YaP Link (proxy).";
    private String connectChannel = "BungeeCord";

    public PortalsConfig(org.bukkit.plugin.java.JavaPlugin plugin) {
        reload(plugin.getConfig());
    }

    public void reload(FileConfiguration c) {
        serverId = c.getString("server-id", "lobby");
        if (serverId == null || serverId.isBlank()) {
            serverId = "lobby";
        }
        enabled = c.getBoolean("enabled", true);
        defaultCooldownSeconds = Math.max(0, c.getInt("defaults.cooldown-seconds", 3));
        defaultPermission = c.getString("defaults.permission", "");
        if (defaultPermission == null) {
            defaultPermission = "";
        }
        defaultColor = PortalColors.normalize(c.getString("defaults.color", PortalColors.DEFAULT));
        msgTransferring = c.getString("messages.transferring", msgTransferring);
        msgDenied = c.getString("messages.denied", msgDenied);
        msgCooldown = c.getString("messages.cooldown", msgCooldown);
        msgAlreadyHere = c.getString("messages.already-here", msgAlreadyHere);
        msgNoProxy = c.getString("messages.no-proxy", msgNoProxy);
        connectChannel = c.getString("connect.channel", "BungeeCord");
        if (connectChannel == null || connectChannel.isBlank()) {
            connectChannel = "BungeeCord";
        }
    }

    public String serverId() {
        return serverId;
    }

    public boolean enabled() {
        return enabled;
    }

    public int defaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    public String defaultPermission() {
        return defaultPermission;
    }

    public String defaultColor() {
        return defaultColor;
    }

    public String msgTransferring() {
        return msgTransferring;
    }

    public String msgDenied() {
        return msgDenied;
    }

    public String msgCooldown() {
        return msgCooldown;
    }

    public String msgAlreadyHere() {
        return msgAlreadyHere;
    }

    public String msgNoProxy() {
        return msgNoProxy;
    }

    public String connectChannel() {
        return connectChannel;
    }
}
