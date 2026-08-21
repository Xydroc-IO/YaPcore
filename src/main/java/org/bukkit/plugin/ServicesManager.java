package org.bukkit.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bukkit ServicesManager — plugins register Vault/economy/etc. here. */
public interface ServicesManager {

    <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority);

    void unregisterAll(Plugin plugin);

    void unregister(Class<?> service, Object provider);

    void unregister(Object provider);

    <T> T load(Class<T> service);

    <T> RegisteredServiceProvider<T> getRegistration(Class<T> service);

    List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin);

    <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service);

    Collection<Class<?>> getKnownServices();

    <T> boolean isProvidedFor(Class<T> service);
}
