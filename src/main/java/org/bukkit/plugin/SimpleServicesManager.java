package org.bukkit.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimpleServicesManager implements ServicesManager {

    private final Map<Class<?>, CopyOnWriteArrayList<RegisteredServiceProvider<?>>> map =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority) {
        map.computeIfAbsent(service, c -> new CopyOnWriteArrayList<>())
                .add(new RegisteredServiceProvider(service, provider, priority, plugin));
        map.get(service).sort(Comparator.comparing((RegisteredServiceProvider<?> r) -> r.getPriority()).reversed());
    }

    @Override
    public void unregisterAll(Plugin plugin) {
        for (CopyOnWriteArrayList<RegisteredServiceProvider<?>> list : map.values()) {
            list.removeIf(r -> r.getPlugin() == plugin);
        }
    }

    @Override
    public void unregister(Class<?> service, Object provider) {
        CopyOnWriteArrayList<RegisteredServiceProvider<?>> list = map.get(service);
        if (list != null) {
            list.removeIf(r -> r.getProvider() == provider);
        }
    }

    @Override
    public void unregister(Object provider) {
        for (CopyOnWriteArrayList<RegisteredServiceProvider<?>> list : map.values()) {
            list.removeIf(r -> r.getProvider() == provider);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(Class<T> service) {
        RegisteredServiceProvider<T> reg = getRegistration(service);
        return reg == null ? null : reg.getProvider();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
        CopyOnWriteArrayList<RegisteredServiceProvider<?>> list = map.get(service);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return (RegisteredServiceProvider<T>) list.get(0);
    }

    @Override
    public List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin) {
        List<RegisteredServiceProvider<?>> out = new ArrayList<>();
        for (CopyOnWriteArrayList<RegisteredServiceProvider<?>> list : map.values()) {
            for (RegisteredServiceProvider<?> r : list) {
                if (r.getPlugin() == plugin) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service) {
        CopyOnWriteArrayList<RegisteredServiceProvider<?>> list = map.get(service);
        if (list == null) {
            return List.of();
        }
        List<RegisteredServiceProvider<T>> out = new ArrayList<>();
        for (RegisteredServiceProvider<?> r : list) {
            out.add((RegisteredServiceProvider<T>) r);
        }
        return out;
    }

    @Override
    public Collection<Class<?>> getKnownServices() {
        return Collections.unmodifiableCollection(map.keySet());
    }

    @Override
    public <T> boolean isProvidedFor(Class<T> service) {
        return getRegistration(service) != null;
    }
}
