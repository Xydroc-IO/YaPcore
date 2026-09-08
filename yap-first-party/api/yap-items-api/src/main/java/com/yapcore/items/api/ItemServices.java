package com.yapcore.items.api;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class ItemServices {

    private ItemServices() {
    }

    public static Optional<ItemService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(ItemService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}
