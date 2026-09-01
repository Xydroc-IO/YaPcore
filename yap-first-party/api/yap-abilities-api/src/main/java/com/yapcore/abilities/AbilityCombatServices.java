package com.yapcore.abilities;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Proxy;
import java.util.Optional;

public final class AbilityCombatServices {

    private AbilityCombatServices() {
    }

    public static Optional<AbilityCombatBridge> find() {
        RegisteredServiceProvider<AbilityCombatBridge> reg =
                Bukkit.getServicesManager().getRegistration(AbilityCombatBridge.class);
        if (reg != null) {
            return Optional.of(reg.getProvider());
        }
        // Combat shades its own copy of this interface — match by FQCN and adapt.
        for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
            if (!AbilityCombatBridge.class.getName().equals(service.getName())) {
                continue;
            }
            RegisteredServiceProvider<?> other = Bukkit.getServicesManager().getRegistration(service);
            if (other == null) {
                continue;
            }
            return Optional.of(adapt(other.getProvider()));
        }
        return Optional.empty();
    }

    private static AbilityCombatBridge adapt(Object provider) {
        if (provider instanceof AbilityCombatBridge bridge) {
            return bridge;
        }
        return (AbilityCombatBridge) Proxy.newProxyInstance(
                AbilityCombatBridge.class.getClassLoader(),
                new Class<?>[] {AbilityCombatBridge.class},
                (proxy, method, args) -> method.invoke(provider, args));
    }
}
