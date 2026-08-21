package com.example.vehicles;

import com.yapcore.vehicles.api.ChassisBlueprint;
import com.yapcore.vehicles.api.ChassisKit;
import com.yapcore.vehicles.api.VehicleAPI;
import com.yapcore.vehicles.api.VehicleController;
import com.yapcore.vehicles.api.VehicleType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Example author plugin — builds {@code cargo_truck} on {@link ChassisKit#truck()}.
 */
public final class CargoTruckAddonPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RegisteredServiceProvider<VehicleAPI> rsp =
                Bukkit.getServicesManager().getRegistration(VehicleAPI.class);
        if (rsp == null) {
            getLogger().warning("YaPVehicles not present — cargo_truck not registered");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        VehicleAPI vehicles = rsp.getProvider();
        vehicles.registerType(cargoTruck());
        getLogger().info("Registered cargo_truck — /yapvehicle spawn cargo_truck");
    }

    @Override
    public void onDisable() {
        RegisteredServiceProvider<VehicleAPI> rsp =
                Bukkit.getServicesManager().getRegistration(VehicleAPI.class);
        if (rsp != null) {
            rsp.getProvider().unregisterType("cargo_truck");
        }
    }

    private static VehicleType cargoTruck() {
        ChassisBlueprint frame = ChassisKit.truck();
        VehicleController truckController = (vehicle, driver, input) -> {
            VehicleController.DEFAULT.apply(vehicle, driver, input);
            if (driver != null && input.boost() && input.throttle() > 0) {
                double rad = Math.toRadians(vehicle.getYaw());
                double sin = -Math.sin(rad);
                double cos = Math.cos(rad);
                input.setExtraForce(new Vector(sin * 0.02, 0, cos * 0.02));
            }
        };

        return VehicleType.builder("cargo_truck")
                .displayName("Cargo Truck")
                .chassis(frame)
                .bodyPanel(frame, "cabin", Material.ORANGE_CONCRETE, 1.5, 0.85, 1.1)
                .bodyPanel(frame, "bed", Material.GRAY_CONCRETE, 1.6, 0.55, 1.5)
                .bodyPanel(frame, "bumper_f", Material.LIGHT_GRAY_CONCRETE, 1.7, 0.25, 0.2)
                .maxSpeed(0.42)
                .acceleration(0.022)
                .brakeForce(0.08)
                .turnRate(3.2)
                .traction(1.1)
                .lateralGrip(0.34)
                .rollingResistance(0.014)
                .yawInertia(0.55)
                .handbrakeGripScale(0.3)
                .fuel(2000, 0.5)
                .health(80)
                .collisionDamageScale(12)
                .controller(truckController)
                .build();
    }
}
