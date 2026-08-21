package com.example.demo;

import com.yapcore.api.YaPPlugin;

/**
 * Example all-in-one YaP plugin (source reference).
 * Package as a jar with yap.yml and drop into plugins/.
 */
public final class DemoAllInOnePlugin extends YaPPlugin {

    @Override
    public void onEnable() {
        getLogger().info("DemoAllInOne enabled — dual-pool ready");
        getScheduler().runUi(() -> getLogger().info("UI pool hello"));
        getScheduler().runHeavy(() -> getLogger().info("Heavy I/O pool hello"));
        getScheduler().runSync(() -> getLogger().info("SYNC bridge hello (game-thread safe)"));
    }

    @Override
    public void onDisable() {
        getLogger().info("DemoAllInOne disabled");
    }
}
