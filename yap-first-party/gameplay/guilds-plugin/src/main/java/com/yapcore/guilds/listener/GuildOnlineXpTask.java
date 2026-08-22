package com.yapcore.guilds.listener;

import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.service.GuildServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuildOnlineXpTask implements Runnable {

    private final JavaPlugin plugin;
    private final GuildsConfig config;
    private final GuildServiceImpl guilds;

    public GuildOnlineXpTask(JavaPlugin plugin, GuildsConfig config, GuildServiceImpl guilds) {
        this.plugin = plugin;
        this.config = config;
        this.guilds = guilds;
    }

    public void start() {
        if (config.onlineTickXp() <= 0) {
            return;
        }
        long period = Math.max(20L, config.onlineIntervalTicks());
        YapSched.asyncTimer(plugin, this, period, period);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            guilds.member(player.getUniqueId()).ifPresent(member ->
                    guilds.addGuildXp(member.guildId(), player.getUniqueId(),
                            config.onlineTickXp(), "online"));
        }
    }
}
