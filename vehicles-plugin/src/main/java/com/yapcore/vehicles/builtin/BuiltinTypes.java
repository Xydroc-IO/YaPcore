package com.yapcore.vehicles.builtin;

import com.yapcore.vehicles.api.ChassisBlueprint;
import com.yapcore.vehicles.api.ChassisKit;
import com.yapcore.vehicles.api.HighResModels;
import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.api.VehicleVisual;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import com.yapcore.vehicles.engine.VehiclesConfig;
import org.bukkit.Material;

/**
 * Default fleet with optional high-res ItemDisplay body models (resource pack).
 */
public final class BuiltinTypes {

    private BuiltinTypes() {
    }

    public static void register(VehicleServiceImpl api, VehiclesConfig config) {
        api.registerType(chassis(config));
        if (config.builtinBuggy()) {
            api.registerType(buggy(config));
        }
        if (config.builtinHoverbike()) {
            api.registerType(hoverbike(config));
        }
        if (config.builtinFleet()) {
            api.registerType(truck4x4(config));
            api.registerType(monsterTruck(config));
            api.registerType(sportCar(config));
            api.registerType(hypercar(config));
            api.registerType(lambo(config));
            api.registerType(ferrari(config));
            api.registerType(mclaren(config));
            api.registerType(porsche(config));
        }
    }

    /** Prefer glass + interior under HD bodies; drop block frame/wheels. */
    private static ChassisBlueprint forVisuals(ChassisBlueprint kit, VehiclesConfig config) {
        if (config.highResModels()) {
            return kit.withoutRoles(VehicleVisual.Role.FRAME, VehicleVisual.Role.WHEEL);
        }
        return kit;
    }

    private static void attachHd(
            VehicleType.Builder b,
            ChassisBlueprint raw,
            VehiclesConfig config,
            int hdCmd,
            String typeId,
            Runnable blockPaint
    ) {
        if (config.highResModels()) {
            b.bodyModel(hdCmd, 0, raw.height() * 0.45, 0, HighResModels.bodyScale(typeId));
            if (config.highResKeepBlockBody()) {
                blockPaint.run();
            }
        } else {
            blockPaint.run();
        }
    }

    public static VehicleType chassis(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.bare();
        var b = VehicleType.builder("chassis").displayName("YaP Chassis").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.CHASSIS, "chassis", () -> {
        });
        return b.maxSpeed(0.4).acceleration(0.028).brakeForce(0.06).turnRate(4.0)
                .traction(0.95).lateralGrip(0.32).rollingResistance(0.01).yawInertia(0.4)
                .rideHeight(0.05).fuel(600, 0.25).health(60).build();
    }

    public static VehicleType buggy(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.car();
        var b = VehicleType.builder("buggy").displayName("Buggy").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.BUGGY, "buggy", () -> {
            b.bodyPanel(raw, "hood", Material.RED_CONCRETE, 1.2, 0.35, 0.7);
            b.bodyPanel(raw, "bed", Material.RED_CONCRETE, 1.15, 0.3, 0.8);
        });
        return b.maxSpeed(0.58).acceleration(0.038).brakeForce(0.07).turnRate(5.2)
                .traction(1.05).lateralGrip(0.26).rollingResistance(0.009).yawInertia(0.32)
                .handbrakeGripScale(0.2).rideHeight(0.08)
                .fuel(1200, 0.4).health(50).collisionDamageScale(10).build();
    }

    public static VehicleType hoverbike(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.bike();
        var b = VehicleType.builder("hoverbike").displayName("Hoverbike").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.HOVERBIKE, "hoverbike", () -> {
            b.visual(VehicleVisual.block(Material.CYAN_CONCRETE).offset(0, 0.55, 0).scale(0.7, 0.25, 1.2).build());
            b.visual(VehicleVisual.block(Material.SEA_LANTERN).offset(0, 0.2, 0).scale(0.55, 0.15, 0.55).build());
        });
        return b.maxSpeed(0.72).acceleration(0.045).brakeForce(0.05).turnRate(7.5)
                .hoverHeight(0.85).gravity(0.04)
                .traction(0.7).lateralGrip(0.18).rollingResistance(0.004).yawInertia(0.22)
                .slopeGrip(0.02).fuel(800, 0.55).health(30).collisionDamageScale(6).build();
    }

    public static VehicleType truck4x4(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.fourByFour();
        var b = VehicleType.builder("truck_4x4").displayName("4x4 Truck").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.TRUCK_4X4, "truck_4x4", () -> {
            b.bodyPanel(raw, "hood", Material.GREEN_CONCRETE, 1.5, 0.4, 0.9);
            b.bodyPanel(raw, "bed", Material.GREEN_TERRACOTTA, 1.6, 0.45, 1.3);
            b.bodyPanel(raw, "bumper_f", Material.IRON_BLOCK, 1.7, 0.25, 0.2);
        });
        return b.maxSpeed(0.48).acceleration(0.032).brakeForce(0.08).turnRate(3.8)
                .traction(1.25).lateralGrip(0.34).rollingResistance(0.014).yawInertia(0.55)
                .slopeGrip(0.08).rideHeight(0.35)
                .fuel(1600, 0.55).health(90).collisionDamageScale(12).build();
    }

    public static VehicleType monsterTruck(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.monster();
        var b = VehicleType.builder("monster_truck").displayName("Monster Truck").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.MONSTER_TRUCK, "monster_truck", () -> {
            b.bodyPanel(raw, "hood", Material.RED_CONCRETE, 1.5, 0.35, 0.8);
            b.bodyPanel(raw, "bumper_f", Material.YELLOW_CONCRETE, 1.8, 0.3, 0.25);
        });
        return b.maxSpeed(0.42).acceleration(0.03).brakeForce(0.09).turnRate(3.2)
                .traction(1.4).lateralGrip(0.4).rollingResistance(0.02).yawInertia(0.7)
                .slopeGrip(0.12).rideHeight(1.15)
                .fuel(1400, 0.7).health(120).collisionDamageScale(18).build();
    }

    public static VehicleType sportCar(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.sport();
        var b = VehicleType.builder("sport_car").displayName("Sport Car").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.SPORT_CAR, "sport_car", () -> {
            b.bodyPanel(raw, "hood", Material.BLUE_CONCRETE, 1.35, 0.28, 0.85);
            b.bodyPanel(raw, "bed", Material.BLUE_CONCRETE, 1.3, 0.25, 0.7);
        });
        return b.maxSpeed(0.78).acceleration(0.052).brakeForce(0.09).turnRate(6.2)
                .traction(1.1).lateralGrip(0.22).rollingResistance(0.006).yawInertia(0.25)
                .handbrakeGripScale(0.18).rideHeight(0.06)
                .fuel(1000, 0.55).health(45).collisionDamageScale(14).build();
    }

    public static VehicleType hypercar(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.hyper();
        var b = VehicleType.builder("hypercar").displayName("Hypercar").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.HYPERCAR, "hypercar", () -> {
            b.bodyPanel(raw, "hood", Material.BLACK_CONCRETE, 1.5, 0.22, 0.9);
            b.bodyPanel(raw, "bed", Material.BLACK_CONCRETE, 1.45, 0.2, 0.75);
            b.bodyPanel(raw, "roof", Material.GRAY_CONCRETE, 1.2, 0.08, 0.9);
        });
        return b.maxSpeed(0.95).acceleration(0.07).brakeForce(0.11).turnRate(6.8)
                .traction(1.15).lateralGrip(0.2).rollingResistance(0.004).yawInertia(0.2)
                .handbrakeGripScale(0.15).rideHeight(0.04)
                .fuel(900, 0.75).health(40).collisionDamageScale(16).build();
    }

    public static VehicleType lambo(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.hyper();
        var b = VehicleType.builder("lambo").displayName("Lambo SV").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.LAMBO, "lambo", () -> {
            b.bodyPanel(raw, "hood", Material.LIME_CONCRETE, 1.55, 0.24, 0.95);
            b.bodyPanel(raw, "bed", Material.LIME_CONCRETE, 1.5, 0.22, 0.8);
            b.bodyPanel(raw, "bumper_f", Material.BLACK_CONCRETE, 1.4, 0.15, 0.2);
        });
        return b.maxSpeed(0.92).acceleration(0.068).brakeForce(0.1).turnRate(6.5)
                .traction(1.12).lateralGrip(0.19).rollingResistance(0.0045).yawInertia(0.22)
                .rideHeight(0.04).fuel(950, 0.72).health(42).collisionDamageScale(15).build();
    }

    public static VehicleType ferrari(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.hyper();
        var b = VehicleType.builder("ferrari").displayName("Ferrari XX").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.FERRARI, "ferrari", () -> {
            b.bodyPanel(raw, "hood", Material.RED_CONCRETE, 1.5, 0.24, 0.9);
            b.bodyPanel(raw, "bed", Material.RED_CONCRETE, 1.45, 0.22, 0.75);
            b.bodyPanel(raw, "bumper_f", Material.BLACK_CONCRETE, 1.35, 0.12, 0.18);
        });
        return b.maxSpeed(0.94).acceleration(0.072).brakeForce(0.105).turnRate(6.7)
                .traction(1.14).lateralGrip(0.18).rollingResistance(0.004).yawInertia(0.2)
                .rideHeight(0.04).fuel(920, 0.78).health(40).collisionDamageScale(16).build();
    }

    public static VehicleType mclaren(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.hyper();
        var b = VehicleType.builder("mclaren").displayName("McLaren GT").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.MCLAREN, "mclaren", () -> {
            b.bodyPanel(raw, "hood", Material.ORANGE_CONCRETE, 1.48, 0.22, 0.88);
            b.bodyPanel(raw, "bed", Material.ORANGE_CONCRETE, 1.42, 0.2, 0.72);
            b.bodyPanel(raw, "roof", Material.BLACK_CONCRETE, 1.1, 0.06, 0.85);
        });
        return b.maxSpeed(0.96).acceleration(0.075).brakeForce(0.11).turnRate(7.0)
                .traction(1.16).lateralGrip(0.17).rollingResistance(0.0035).yawInertia(0.18)
                .rideHeight(0.035).fuel(880, 0.8).health(38).collisionDamageScale(17).build();
    }

    public static VehicleType porsche(VehiclesConfig config) {
        ChassisBlueprint raw = ChassisKit.sport();
        var b = VehicleType.builder("porsche").displayName("Porsche Turbo").chassis(forVisuals(raw, config));
        attachHd(b, raw, config, HighResModels.PORSCHE, "porsche", () -> {
            b.bodyPanel(raw, "hood", Material.WHITE_CONCRETE, 1.35, 0.26, 0.8);
            b.bodyPanel(raw, "bed", Material.LIGHT_GRAY_CONCRETE, 1.3, 0.28, 0.85);
            b.bodyPanel(raw, "bumper_f", Material.BLACK_CONCRETE, 1.25, 0.14, 0.18);
        });
        return b.maxSpeed(0.82).acceleration(0.055).brakeForce(0.095).turnRate(6.0)
                .traction(1.18).lateralGrip(0.24).rollingResistance(0.0055).yawInertia(0.24)
                .rideHeight(0.05).fuel(1050, 0.6).health(48).collisionDamageScale(13).build();
    }
}
