package com.yapcore.portals;

import org.bukkit.Material;
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

    private boolean endDoorsEnabled = true;
    private Material endDoorFrame = Material.OBSIDIAN;
    private Material endDoorInterior = Material.NETHER_PORTAL;
    private Material endDoorActivateItem = Material.ENDER_EYE;
    private int endDoorWidth = 4;
    private int endDoorHeight = 5;
    private String endDoorWorld = "world_the_end";
    private int endDoorCooldownSeconds = 3;

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

        endDoorsEnabled = c.getBoolean("end-doors.enabled", true);
        endDoorFrame = material(c.getString("end-doors.frame", "OBSIDIAN"), Material.OBSIDIAN);
        endDoorInterior = material(c.getString("end-doors.interior", "NETHER_PORTAL"), Material.NETHER_PORTAL);
        endDoorActivateItem = material(c.getString("end-doors.activate-item", "ENDER_EYE"), Material.ENDER_EYE);
        endDoorWidth = Math.max(3, c.getInt("end-doors.width", 4));
        endDoorHeight = Math.max(4, c.getInt("end-doors.height", 5));
        endDoorWorld = c.getString("end-doors.end-world", "world_the_end");
        if (endDoorWorld == null || endDoorWorld.isBlank()) {
            endDoorWorld = "world_the_end";
        }
        endDoorCooldownSeconds = Math.max(0, c.getInt("end-doors.cooldown-seconds", 3));
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
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

    public boolean endDoorsEnabled() {
        return endDoorsEnabled;
    }

    public Material endDoorFrame() {
        return endDoorFrame;
    }

    public Material endDoorInterior() {
        return endDoorInterior;
    }

    public Material endDoorActivateItem() {
        return endDoorActivateItem;
    }

    public int endDoorWidth() {
        return endDoorWidth;
    }

    public int endDoorHeight() {
        return endDoorHeight;
    }

    public String endDoorWorld() {
        return endDoorWorld;
    }

    public int endDoorCooldownSeconds() {
        return endDoorCooldownSeconds;
    }
}
