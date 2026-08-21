package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;

@FunctionalInterface
public interface EventExecutor {
    void execute(Listener listener, Event event) throws Exception;
}
