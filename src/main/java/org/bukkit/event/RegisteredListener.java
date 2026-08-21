package org.bukkit.event;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class RegisteredListener {
    private final Listener listener;
    private final Method method;
    private final EventPriority priority;
    private final Plugin plugin;
    private final boolean ignoreCancelled;

    public RegisteredListener(Listener listener, Method method, EventPriority priority,
                              Plugin plugin, boolean ignoreCancelled) {
        this.listener = listener;
        this.method = method;
        this.priority = priority;
        this.plugin = plugin;
        this.ignoreCancelled = ignoreCancelled;
        this.method.setAccessible(true);
    }

    public Listener getListener() { return listener; }
    public Method getMethod() { return method; }
    public EventPriority getPriority() { return priority; }
    public Plugin getPlugin() { return plugin; }
    public boolean isIgnoringCancelled() { return ignoreCancelled; }

    public void callEvent(Event event) throws Exception {
        if (event instanceof Cancellable cancellable
                && cancellable.isCancelled()
                && ignoreCancelled) {
            return;
        }
        method.invoke(listener, event);
    }
}
