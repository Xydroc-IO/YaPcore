package org.bukkit.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class HandlerList {
    private static final CopyOnWriteArraySet<HandlerList> ALL = new CopyOnWriteArraySet<>();

    private final EnumMap<EventPriority, CopyOnWriteArrayList<RegisteredListener>> handlers =
            new EnumMap<>(EventPriority.class);

    public HandlerList() {
        for (EventPriority p : EventPriority.values()) {
            handlers.put(p, new CopyOnWriteArrayList<>());
        }
        ALL.add(this);
    }

    public void register(RegisteredListener listener) {
        handlers.get(listener.getPriority()).add(listener);
    }

    public void unregister(RegisteredListener listener) {
        handlers.get(listener.getPriority()).remove(listener);
    }

    public void unregister(org.bukkit.plugin.Plugin plugin) {
        for (CopyOnWriteArrayList<RegisteredListener> list : handlers.values()) {
            list.removeIf(rl -> rl.getPlugin() == plugin);
        }
    }

    /** Unregister a plugin from every known HandlerList. */
    public static void unregisterAll(org.bukkit.plugin.Plugin plugin) {
        for (HandlerList list : ALL) {
            list.unregister(plugin);
        }
    }

    public static void unregisterAll() {
        for (HandlerList list : ALL) {
            for (CopyOnWriteArrayList<RegisteredListener> slot : list.handlers.values()) {
                slot.clear();
            }
        }
    }

    public List<RegisteredListener> getRegisteredListeners() {
        List<RegisteredListener> all = new ArrayList<>();
        for (EventPriority p : EventPriority.values()) {
            all.addAll(handlers.get(p));
        }
        return all;
    }

    public Map<EventPriority, CopyOnWriteArrayList<RegisteredListener>> getHandlerSlots() {
        return handlers;
    }
}
