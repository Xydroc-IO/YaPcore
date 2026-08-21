package org.bukkit.plugin;

public final class RegisteredServiceProvider<T> {

    private final Class<T> service;
    private final T provider;
    private final ServicePriority priority;
    private final Plugin plugin;

    public RegisteredServiceProvider(Class<T> service, T provider, ServicePriority priority, Plugin plugin) {
        this.service = service;
        this.provider = provider;
        this.priority = priority;
        this.plugin = plugin;
    }

    public Class<T> getService() {
        return service;
    }

    public T getProvider() {
        return provider;
    }

    public ServicePriority getPriority() {
        return priority;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
