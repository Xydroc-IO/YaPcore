package com.yapcore.pregen;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PregenPlugin extends JavaPlugin {

    private PregenConfig pregenConfig;
    private PregenService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pregenConfig = new PregenConfig();
        pregenConfig.load(getConfig());
        ProgressStore store = new ProgressStore(getDataFolder(), getLogger());
        service = new PregenService(this, pregenConfig, store);
        service.start();

        PluginCommand cmd = getCommand("yappregen");
        if (cmd != null) {
            PregenCommand exec = new PregenCommand(this, service);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
        getLogger().info("YaP Pregen online — /yappregen start <world> radius <chunks>");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.shutdown();
        }
    }

    public void reloadPregenConfig() {
        reloadConfig();
        pregenConfig.load(getConfig());
    }

    public PregenService service() {
        return service;
    }
}
