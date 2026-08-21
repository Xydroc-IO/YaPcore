package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.io.File;

public interface PluginLoader {
    Plugin loadPlugin(File file) throws Exception;

    PluginDescriptionFile getPluginDescription(File file) throws Exception;

    void enablePlugin(Plugin plugin);

    void disablePlugin(Plugin plugin);

    Class<? extends Event>[] createRegisteredListeners(Listener listener, Plugin plugin);
}
