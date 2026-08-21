package com.yapcore.api.module;

import com.yapcore.api.YaPScheduler;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Fine-tune server module — drop a jar with {@code module.yml} into {@code modules/}.
 * Same pool routing as YaP plugins: UI / HEAVY / SYNC.
 */
public abstract class YaPModule {

    private YaPModuleContext context;
    private boolean enabled;

    public final void init(YaPModuleContext context) {
        this.context = context;
    }

    public final YaPModuleContext getContext() {
        return context;
    }

    public final YaPScheduler getScheduler() {
        return context.scheduler();
    }

    public final Logger getLogger() {
        return context.logger();
    }

    public final File getDataFolder() {
        return context.dataFolder();
    }

    public final YaPModuleDescription getDescription() {
        return context.description();
    }

    public final String getName() {
        return getDescription().name();
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void enable() {
        if (enabled) {
            return;
        }
        enabled = true;
        onEnable();
    }

    public final void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        onDisable();
    }

    public void onLoad() {
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public InputStream getResource(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }
}
