package com.yapcore.compat;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.RegisteredListener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleBukkitPluginManager implements PluginManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.BukkitPM");

    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private final Map<Class<? extends Event>, HandlerList> extraHandlers = new ConcurrentHashMap<>();
    private PluginLoader defaultLoader;

    public void setDefaultLoader(PluginLoader loader) {
        this.defaultLoader = loader;
    }

    @Override
    public void registerInterface(Class<? extends PluginLoader> loader) {
        // single loader path for YaPcore
    }

    @Override
    public Plugin getPlugin(String name) {
        return plugins.get(name.toLowerCase());
    }

    @Override
    public Plugin[] getPlugins() {
        return plugins.values().toArray(Plugin[]::new);
    }

    @Override
    public boolean isPluginEnabled(String name) {
        Plugin p = getPlugin(name);
        return p != null && p.isEnabled();
    }

    @Override
    public boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public Plugin loadPlugin(File file) throws Exception {
        if (defaultLoader == null) {
            throw new IllegalStateException("No plugin loader");
        }
        Plugin plugin = defaultLoader.loadPlugin(file);
        plugins.put(plugin.getName().toLowerCase(), plugin);
        return plugin;
    }

    @Override
    public Plugin[] loadPlugins(File directory) {
        List<Plugin> loaded = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) ->
                name.endsWith(".jar") || name.endsWith(".yap"));
        if (files == null) {
            return new Plugin[0];
        }
        for (File file : files) {
            try {
                // Skip next-gen yap.yml-only jars here; PluginRuntime handles detection
                loaded.add(loadPlugin(file));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Could not load " + file.getName(), e);
            }
        }
        return loaded.toArray(Plugin[]::new);
    }

    @Override
    public void disablePlugins() {
        for (Plugin plugin : new ArrayList<>(plugins.values())) {
            disablePlugin(plugin);
        }
    }

    @Override
    public void clearPlugins() {
        disablePlugins();
        plugins.clear();
    }

    @Override
    public void callEvent(Event event) {
        dispatch(event.getHandlers(), event);
        HandlerList extra = extraHandlers.get(event.getClass());
        if (extra != null) {
            dispatch(extra, event);
        }
    }

    private void dispatch(HandlerList list, Event event) {
        for (RegisteredListener rl : list.getRegisteredListeners()) {
            try {
                rl.callEvent(event);
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Could not pass event " + event.getEventName()
                        + " to " + rl.getPlugin().getName(), t);
                com.yapcore.crash.CrashLogger.get().logPluginFault(
                        rl.getPlugin().getName(), "event:" + event.getEventName(), t);
            }
        }
    }

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(param)) {
                continue;
            }
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) param;
            EventExecutor executor = (l, e) -> method.invoke(l, e);
            registerEvent(eventClass, listener, handler.priority(), executor, plugin);
            try {
                Method getHandlerList = eventClass.getMethod("getHandlerList");
                HandlerList hl = (HandlerList) getHandlerList.invoke(null);
                hl.register(new RegisteredListener(listener, method, handler.priority(),
                        plugin, handler.ignoreCancelled()));
            } catch (ReflectiveOperationException e) {
                // Fall back to manager-owned list when stub events lack HandlerList
                extraHandlers.computeIfAbsent(eventClass, c -> new HandlerList())
                        .register(new RegisteredListener(listener, method, handler.priority(),
                                plugin, handler.ignoreCancelled()));
                LOG.fine("Using extra HandlerList for " + eventClass.getName());
            }
        }
    }

    @Override
    public void registerEvent(Class<? extends Event> event, Listener listener,
                              EventPriority priority, EventExecutor executor, Plugin plugin) {
        // Annotated path registers on the event HandlerList; this stores executors for API parity.
        extraHandlers.computeIfAbsent(event, c -> new HandlerList());
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        if (plugin.isEnabled()) {
            return;
        }
        try {
            defaultLoader.enablePlugin(plugin);
        } catch (org.bukkit.plugin.UnknownDependencyException e) {
            LOG.fine("Defer enable for " + plugin.getName() + ": " + e.getMessage());
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Enable crashed for " + plugin.getName() + " — continuing", t);
            com.yapcore.crash.CrashLogger.get().logPluginFault(plugin.getName(), "enable", t);
        }
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (!plugin.isEnabled()) {
            return;
        }
        HandlerList.unregisterAll(plugin);
        for (HandlerList extra : extraHandlers.values()) {
            extra.unregister(plugin);
        }
        defaultLoader.disablePlugin(plugin);
        LOG.info("Disabled plugin " + plugin.getName());
    }

    @Override
    public Set<Plugin> getLoadedPlugins() {
        return Set.copyOf(plugins.values());
    }

    public void registerLoaded(Plugin plugin) {
        plugins.put(plugin.getName().toLowerCase(), plugin);
    }
}
