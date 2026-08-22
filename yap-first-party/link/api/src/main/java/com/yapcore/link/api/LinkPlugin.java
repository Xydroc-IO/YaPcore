package com.yapcore.link.api;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * YaP Link plugin entry point. Not compatible with Velocity's plugin API.
 *
 * <p>Place plugin jars in {@code link-data/plugins/} with a {@code link-plugin.json} descriptor.
 */
public interface LinkPlugin {

    default void onLoad(LinkPluginContext context) {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }

    /** Called by the loader after construction; plugins may store context here. */
    interface LinkPluginContext {
        LinkProxy proxy();

        LinkPluginDescription description();

        Path dataDirectory();

        Logger logger();
    }
}
