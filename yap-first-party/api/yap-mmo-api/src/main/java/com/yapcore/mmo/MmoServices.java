package com.yapcore.mmo;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class MmoServices {

    private MmoServices() {
    }

    public static Optional<MmoSnapshotService> snapshot() {
        var reg = Bukkit.getServicesManager().getRegistration(MmoSnapshotService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}
