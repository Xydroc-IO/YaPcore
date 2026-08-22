package com.yapcore.combat.listener;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.service.PrayerService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrayerDrainListener implements Runnable {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final PrayerService prayers;

    public PrayerDrainListener(JavaPlugin plugin, CombatConfig config, PrayerService prayers) {
        this.plugin = plugin;
        this.config = config;
        this.prayers = prayers;
    }

    public void start() {
        long period = Math.max(5L, config.prayerDrainIntervalTicks());
        YapSched.globalTimer(plugin, this, period, period);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> prayers.drainTick(player));
        }
    }
}
