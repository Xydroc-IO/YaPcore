package com.yapcore.admin;

import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.cmd.AdminCommands;
import com.yapcore.admin.cmd.YapPluginsCommand;
import com.yapcore.admin.gui.AdminMenuListener;
import com.yapcore.admin.gui.AdminMenus;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminPlugin extends JavaPlugin {

    private AdminConfig config;
    private AdminMenus menus;
    private AdminActions actions;
    private final Map<UUID, AdminSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new AdminConfig();
        config.reload(getConfig());
        actions = new AdminActions(this);
        menus = new AdminMenus(this);
        getServer().getPluginManager().registerEvents(new AdminMenuListener(this), this);
        AdminCommands cmds = new AdminCommands(this);
        var yapadmin = getCommand("yapadmin");
        if (yapadmin != null) {
            yapadmin.setExecutor(cmds);
            yapadmin.setTabCompleter(cmds);
        }
        YapPluginsCommand pluginsCmd = new YapPluginsCommand(this);
        var yapplugins = getCommand("yapplugins");
        if (yapplugins != null) {
            yapplugins.setExecutor(pluginsCmd);
            yapplugins.setTabCompleter(pluginsCmd);
        }
        getLogger().info("YaPAdmin ready (/yapadmin, /yapplugins).");
    }

    @Override
    public void onDisable() {
        sessions.clear();
    }

    public void reloadAdminConfig() {
        reloadConfig();
        config.reload(getConfig());
    }

    public AdminConfig adminConfig() {
        return config;
    }

    public AdminMenus menus() {
        return menus;
    }

    public AdminActions actions() {
        return actions;
    }

    public AdminSession session(UUID uuid) {
        return sessions.computeIfAbsent(uuid, id -> new AdminSession());
    }

    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }
}
