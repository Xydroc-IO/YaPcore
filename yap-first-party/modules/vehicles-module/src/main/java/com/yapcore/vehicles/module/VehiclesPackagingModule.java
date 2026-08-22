package com.yapcore.vehicles.module;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Thin operator packaging module — does not run physics.
 * Declares {@code provides: [vehicles]} for other modules' {@code requires}.
 * Runtime engine is the Paper plugin {@code YaPVehicles} in {@code plugins/}.
 */
public final class VehiclesPackagingModule extends YaPModule {

    @Override
    public void onEnable() {
        try {
            Plugin paper = Bukkit.getPluginManager().getPlugin("YaPVehicles");
            if (paper != null && paper.isEnabled()) {
                getLogger().info("YaP Vehicles module OK — Paper plugin YaPVehicles is online");
            } else {
                getLogger().warning(
                        "YaP Vehicles module loaded, but plugins/yap-vehicles.jar (YaPVehicles) "
                                + "is missing or disabled. Install the Paper plugin for real vehicles.");
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            getLogger().warning(
                    "Bukkit not available in this process — drop yap-vehicles.jar into plugins/ "
                            + "under game-authority=paper for the vehicle engine.");
        }
    }
}
