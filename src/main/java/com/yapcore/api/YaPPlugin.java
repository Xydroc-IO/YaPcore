package com.yapcore.api;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Next-generation YaPcore plugin.
 * Designed for all-in-one plugins (GUI + economy + DB) that stay lag-free
 * by routing UI work to the high-speed pool, I/O to the heavy pool, and
 * world mutations through the Compatibility Bridge (SYNC).
 */
public abstract class YaPPlugin {

    private YaPPluginContext context;
    private boolean enabled;

    /** Called by the plugin runtime after construction. */
    public final void init(YaPPluginContext context) {
        this.context = context;
    }

    public final YaPPluginContext getContext() {
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

    public final YaPPluginDescription getDescription() {
        return context.description();
    }

    public final String getName() {
        return getDescription().name();
    }

    public final boolean isEnabled() {
        return enabled;
    }

    final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** Used by the plugin runtime when enabling/disabling. */
    public final void enable() {
        setEnabled(true);
    }

    public final void disable() {
        setEnabled(false);
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
