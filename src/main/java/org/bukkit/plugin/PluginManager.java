package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.Set;

public interface PluginManager {
    void registerInterface(Class<? extends PluginLoader> loader) throws Exception;

    Plugin getPlugin(String name);

    Plugin[] getPlugins();

    boolean isPluginEnabled(String name);

    boolean isPluginEnabled(Plugin plugin);

    Plugin loadPlugin(File file) throws Exception;

    Plugin[] loadPlugins(File directory);

    void disablePlugins();

    void clearPlugins();

    void callEvent(Event event);

    void registerEvents(Listener listener, Plugin plugin);

    void registerEvent(Class<? extends Event> event, Listener listener,
                       EventPriority priority, EventExecutor executor, Plugin plugin);

    void enablePlugin(Plugin plugin);

    void disablePlugin(Plugin plugin);

    Set<Plugin> getLoadedPlugins();
}
