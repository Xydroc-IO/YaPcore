package com.yapcore.smoke;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal Paper plugin for compatibility smoke — must enable on real Paper.
 */
public final class CompatSmokePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("YaP-COMPAT-SMOKE enabled api=" + Bukkit.getVersion()
                + " bukkit=" + Bukkit.getBukkitVersion()
                + " players=" + Bukkit.getOnlinePlayers().size());
        // Touch common surfaces plugins use
        Bukkit.getScheduler().runTask(this, () ->
                getLogger().info("YaP-COMPAT-SMOKE scheduler-ok"));
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
        }, this);
        getLogger().info("YaP-COMPAT-SMOKE event-register-ok");
    }
}
