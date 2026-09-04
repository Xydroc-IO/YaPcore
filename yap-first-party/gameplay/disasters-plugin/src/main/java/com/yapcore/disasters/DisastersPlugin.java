package com.yapcore.disasters;

import com.yapcore.disasters.cmd.DisasterCommands;
import com.yapcore.disasters.gui.DisasterGui;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DisastersPlugin extends JavaPlugin {

    private DisastersConfig config;
    private DisasterManager manager;
    private DisasterGui gui;
    private WarningService warnings;
    private RandomEventScheduler randomEvents;
    private VolcanoSiteService volcanoSites;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new DisastersConfig(this);
        config.reload();
        volcanoSites = new VolcanoSiteService(this);
        volcanoSites.reload();
        warnings = new WarningService(this);
        manager = new DisasterManager(this);
        randomEvents = new RandomEventScheduler(this);
        gui = new DisasterGui(this);

        DisasterCommands commands = new DisasterCommands(this);
        PluginCommand cmd = getCommand("yapdisaster");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(gui, this);

        volcanoSites.startAmbient();
        randomEvents.reload();

        getLogger().info("YaPDisasters Phase 3–5a ready — grief=" + config.grief()
                + " random=" + config.randomEnabled()
                + " warnings=" + config.warningsEnabled()
                + " volcano-sites=" + volcanoSites.all().size()
                + " protect-claims=" + config.protectClaims()
                + " protect-regions=" + config.protectRegions());
    }

    @Override
    public void onDisable() {
        if (randomEvents != null) {
            randomEvents.shutdown();
        }
        if (warnings != null) {
            warnings.shutdown();
        }
        if (volcanoSites != null) {
            volcanoSites.shutdown();
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadDisasters() {
        config.reload();
        volcanoSites.reload();
        volcanoSites.startAmbient();
        randomEvents.reload();
    }

    public DisastersConfig config() {
        return config;
    }

    public DisasterManager manager() {
        return manager;
    }

    public DisasterGui gui() {
        return gui;
    }

    public WarningService warnings() {
        return warnings;
    }

    public RandomEventScheduler randomEvents() {
        return randomEvents;
    }

    public VolcanoSiteService volcanoSites() {
        return volcanoSites;
    }
}
