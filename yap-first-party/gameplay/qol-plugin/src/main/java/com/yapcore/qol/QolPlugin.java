package com.yapcore.qol;

import com.yapcore.qol.gui.QolAdminGui;
import com.yapcore.qol.listener.ExcavatorListener;
import com.yapcore.qol.listener.TimberListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YaP-QoL — VIP timber axe + area excavator (staff-granted tools).
 * Threading: nested BlockBreakEvent + breakNaturally on region threads via YapSched.
 */
public final class QolPlugin extends JavaPlugin {

    private QolConfig config;
    private QolKeys keys;
    private QolItems items;
    private BlockBreakHelper breaks;
    private QolAdminGui adminGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new QolConfig(this);
        config.reload();
        keys = new QolKeys(this);
        items = new QolItems(config, keys);
        breaks = new BlockBreakHelper(this);
        adminGui = new QolAdminGui(this);

        getServer().getPluginManager().registerEvents(new TimberListener(config, items, breaks), this);
        getServer().getPluginManager().registerEvents(new ExcavatorListener(config, items, breaks), this);
        getServer().getPluginManager().registerEvents(adminGui, this);

        QolCommands commands = new QolCommands(this, config, items, adminGui);
        PluginCommand cmd = getCommand("yapqol");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("YaP-QoL online — timber=" + config.timberEnabled()
                + " excavator=" + config.excavatorEnabled()
                + " sizes=" + config.excavatorSizes());
        getLogger().info("Staff: /yapqol gui · /yapqol give timber_axe|excavator:3|6|9 [player]");
    }

    @Override
    public void onDisable() {
        adminGui = null;
        breaks = null;
        items = null;
        keys = null;
        config = null;
    }

    public void reloadQol() {
        reloadConfig();
        if (config != null) {
            config.reload();
        }
    }

    public QolConfig qolConfig() {
        return config;
    }

    public QolItems items() {
        return items;
    }

    public QolAdminGui adminGui() {
        return adminGui;
    }
}
