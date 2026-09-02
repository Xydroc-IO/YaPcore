package com.yapcore.world;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class WorldServices {

    private WorldServices() {
    }

    public static Optional<WorldManagerService> worldManager() {
        RegisteredServiceProvider<WorldManagerService> rsp =
                Bukkit.getServicesManager().getRegistration(WorldManagerService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        WorldManagerService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }

    public static Optional<SelectionService> selection() {
        RegisteredServiceProvider<SelectionService> rsp =
                Bukkit.getServicesManager().getRegistration(SelectionService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        SelectionService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }

    public static Optional<EditApplyService> editApply() {
        RegisteredServiceProvider<EditApplyService> rsp =
                Bukkit.getServicesManager().getRegistration(EditApplyService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        EditApplyService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}
