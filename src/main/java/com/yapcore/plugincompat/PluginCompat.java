package com.yapcore.plugincompat;

import com.yapcore.config.ServerConfig;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: rewrite Paper 1.20–1.21 plugin jars for Paper 26.2 before Paper loads them.
 */
public final class PluginCompat {

    private static final Logger LOG = Logger.getLogger("YaPcore.PluginCompat");

    private PluginCompat() {
    }

    public static void preparePlugins(Path pluginsDir, ServerConfig config) {
        if (pluginsDir == null || config == null || !config.isPluginCompatEnabled()) {
            return;
        }
        if (!config.isPluginCompatRewrite()) {
            LOG.info("Plugin compat enabled but rewrite=false — runtime shims only (yap-plugin-compat)");
            return;
        }
        try {
            int n = new PluginCompatRewriter(config.isPluginCompatBackup())
                    .rewritePluginsDir(pluginsDir);
            if (n == 0) {
                LOG.info("Plugin compat: no jar rewrites needed under " + pluginsDir);
            } else {
                LOG.info("Plugin compat: rewrote " + n + " jar(s) for 1.20–1.21 → 26.2 under " + pluginsDir);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Plugin compat rewrite failed (plugins may still load)", e);
        }
    }
}
