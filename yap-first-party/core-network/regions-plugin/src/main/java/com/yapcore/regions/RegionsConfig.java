package com.yapcore.regions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionsConfig {

    private final JavaPlugin plugin;
    private String serverId = "default";

    private boolean notifyEnabled = true;
    private boolean notifyTitle = true;
    private boolean notifyActionBar = true;
    private boolean notifyChat = false;
    private int notifyFadeInMs = 200;
    private int notifyStayMs = 1800;
    private int notifyFadeOutMs = 400;
    private String notifyEnterTitle = "&aEntering";
    private String notifyEnterSubtitle = "&f{region}";
    private String notifyLeaveTitle = "&7Leaving";
    private String notifyLeaveSubtitle = "&f{region}";
    private String notifyActionBarEnter = "&a» &f{region}";
    private String notifyActionBarLeave = "&7« &f{region}";

    public RegionsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fixed server id for unit tests (no Bukkit plugin). */
    public RegionsConfig(String serverId) {
        this.plugin = null;
        this.serverId = serverId == null || serverId.isBlank() ? "default" : serverId.trim();
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        serverId = c.getString("server-id", "default");

        notifyEnabled = c.getBoolean("notify.enabled", true);
        notifyTitle = c.getBoolean("notify.title", true);
        notifyActionBar = c.getBoolean("notify.action-bar", true);
        notifyChat = c.getBoolean("notify.chat", false);
        notifyFadeInMs = Math.max(0, c.getInt("notify.fade-in-ms", 200));
        notifyStayMs = Math.max(200, c.getInt("notify.stay-ms", 1800));
        notifyFadeOutMs = Math.max(0, c.getInt("notify.fade-out-ms", 400));
        notifyEnterTitle = c.getString("notify.enter-title", "&aEntering");
        notifyEnterSubtitle = c.getString("notify.enter-subtitle", "&f{region}");
        notifyLeaveTitle = c.getString("notify.leave-title", "&7Leaving");
        notifyLeaveSubtitle = c.getString("notify.leave-subtitle", "&f{region}");
        notifyActionBarEnter = c.getString("notify.action-bar-enter", "&a» &f{region}");
        notifyActionBarLeave = c.getString("notify.action-bar-leave", "&7« &f{region}");
    }

    public String serverId() {
        return serverId;
    }

    public boolean notifyEnabled() {
        return notifyEnabled;
    }

    public boolean notifyTitle() {
        return notifyTitle;
    }

    public boolean notifyActionBar() {
        return notifyActionBar;
    }

    public boolean notifyChat() {
        return notifyChat;
    }

    public int notifyFadeInMs() {
        return notifyFadeInMs;
    }

    public int notifyStayMs() {
        return notifyStayMs;
    }

    public int notifyFadeOutMs() {
        return notifyFadeOutMs;
    }

    public String notifyEnterTitle() {
        return notifyEnterTitle;
    }

    public String notifyEnterSubtitle() {
        return notifyEnterSubtitle;
    }

    public String notifyLeaveTitle() {
        return notifyLeaveTitle;
    }

    public String notifyLeaveSubtitle() {
        return notifyLeaveSubtitle;
    }

    public String notifyActionBarEnter() {
        return notifyActionBarEnter;
    }

    public String notifyActionBarLeave() {
        return notifyActionBarLeave;
    }
}
