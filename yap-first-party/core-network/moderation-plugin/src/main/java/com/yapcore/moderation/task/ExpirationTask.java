package com.yapcore.moderation.task;

import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExpirationTask implements Runnable {

    private final JavaPlugin plugin;
    private final ModerationServiceImpl service;

    public ExpirationTask(JavaPlugin plugin, ModerationServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        YapSched.globalTimer(plugin, this, 20L * 60L, 20L * 60L);
    }

    @Override
    public void run() {
        YapSched.async(plugin, service::reload);
    }
}
