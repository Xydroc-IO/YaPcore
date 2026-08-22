package com.yapcore.vehicles.upgrades;

import com.yapcore.vehicles.api.StatModifier;
import com.yapcore.vehicles.api.UpgradeSlot;
import com.yapcore.vehicles.api.VehicleUpgrade;
import org.bukkit.Material;

/**
 * Default upgrades: engines, tire compounds, tire sizes, lift kits, armor, etc.
 * CustomModelData 77101–77125 match resourcepacks/yap-vehicles.
 */
public final class BuiltinUpgrades {

    private BuiltinUpgrades() {
    }

    public static void registerAll(UpgradeService upgrades) {
        // Engine / utility (existing)
        upgrades.register(turboEngine());
        upgrades.register(ecoCarb());
        upgrades.register(reinforcedArmor());
        upgrades.register(fuelTankXl());
        upgrades.register(racingSuspension());
        upgrades.register(nitroKit());

        // Tire compounds
        upgrades.register(tiresStreet());
        upgrades.register(tiresSport());
        upgrades.register(tiresOffroad());
        upgrades.register(tiresMud());
        upgrades.register(tiresSlick());

        // Tire / wheel sizes
        upgrades.register(wheelsStockPlus());
        upgrades.register(wheelsPlusTwo());
        upgrades.register(wheelsDeepDish());
        upgrades.register(wheelsMonster());

        // Lift kits
        upgrades.register(liftTwo());
        upgrades.register(liftFour());
        upgrades.register(liftSix());
        upgrades.register(liftMonster());
    }

    public static VehicleUpgrade turboEngine() {
        return VehicleUpgrade.builder("turbo_engine")
                .displayName("Turbo Engine")
                .slot(UpgradeSlot.ENGINE)
                .lore("+25% speed", "+35% acceleration", "+20% fuel burn")
                .icon(Material.PAPER, 77101)
                .stats(StatModifier.builder().maxSpeedMul(1.25).accelerationMul(1.35).fuelBurnMul(1.20).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.REDSTONE_BLOCK, 1).craft(Material.COAL, 4)
                .shopPrice(Material.IRON_INGOT, 12).shopPrice(Material.COAL, 16).shopSlot(28)
                .build();
    }

    public static VehicleUpgrade ecoCarb() {
        return VehicleUpgrade.builder("eco_carburetor")
                .displayName("Eco Carburetor")
                .slot(UpgradeSlot.ENGINE)
                .lore("−30% fuel burn", "−8% acceleration")
                .icon(Material.PAPER, 77102)
                .stats(StatModifier.builder().fuelBurnMul(0.70).accelerationMul(0.92).build())
                .craft(Material.COPPER_INGOT, 4).craft(Material.COAL, 2).craft(Material.GLASS, 2)
                .shopPrice(Material.COPPER_INGOT, 8).shopPrice(Material.COAL, 8).shopSlot(29)
                .build();
    }

    public static VehicleUpgrade reinforcedArmor() {
        return VehicleUpgrade.builder("reinforced_armor")
                .displayName("Reinforced Armor")
                .slot(UpgradeSlot.ARMOR)
                .lore("+40 HP", "−5% max speed")
                .icon(Material.PAPER, 77104)
                .stats(StatModifier.builder().maxHealthAdd(40).maxSpeedMul(0.95).build())
                .craft(Material.IRON_BLOCK, 2).craft(Material.DIAMOND, 1).craft(Material.COAL, 4)
                .shopPrice(Material.DIAMOND, 3).shopPrice(Material.IRON_INGOT, 16).shopSlot(30)
                .build();
    }

    public static VehicleUpgrade fuelTankXl() {
        return VehicleUpgrade.builder("fuel_tank_xl")
                .displayName("XL Fuel Tank")
                .slot(UpgradeSlot.TANK)
                .lore("+500 max fuel capacity")
                .icon(Material.PAPER, 77105)
                .stats(StatModifier.builder().maxFuelAdd(500).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.BUCKET, 1).craft(Material.COAL_BLOCK, 1)
                .shopPrice(Material.IRON_INGOT, 14).shopPrice(Material.COAL, 24).shopSlot(31)
                .build();
    }

    public static VehicleUpgrade racingSuspension() {
        return VehicleUpgrade.builder("racing_suspension")
                .displayName("Racing Suspension")
                .slot(UpgradeSlot.UTILITY)
                .lore("+30% turn rate", "+10% traction")
                .icon(Material.PAPER, 77106)
                .stats(StatModifier.builder().turnRateMul(1.30).tractionMul(1.10).build())
                .craft(Material.IRON_INGOT, 6).craft(Material.PISTON, 2).craft(Material.SLIME_BLOCK, 1)
                .shopPrice(Material.IRON_INGOT, 10).shopPrice(Material.REDSTONE, 8).shopSlot(32)
                .build();
    }

    public static VehicleUpgrade nitroKit() {
        return VehicleUpgrade.builder("nitro_kit")
                .displayName("Nitro Kit")
                .slot(UpgradeSlot.UTILITY)
                .lore("+40% sprint boost", "+15% fuel burn")
                .icon(Material.PAPER, 77107)
                .stats(StatModifier.builder().boostMul(1.40).fuelBurnMul(1.15).build())
                .craft(Material.FIRE_CHARGE, 2).craft(Material.GUNPOWDER, 4).craft(Material.DIAMOND, 1).craft(Material.COAL, 2)
                .shopPrice(Material.DIAMOND, 2).shopPrice(Material.COAL, 20).shopSlot(33)
                .build();
    }

    // ---- Tire compounds (TIRES slot) ----

    public static VehicleUpgrade tiresStreet() {
        return VehicleUpgrade.builder("tires_street")
                .displayName("Street Tires")
                .slot(UpgradeSlot.TIRES)
                .lore("Balanced all-round rubber")
                .icon(Material.PAPER, 77110)
                .stats(StatModifier.builder().tractionMul(1.05).lateralGripMul(1.05).build())
                .craft(Material.BLACK_DYE, 2).craft(Material.LEATHER, 4)
                .shopPrice(Material.LEATHER, 6).shopPrice(Material.COAL, 2).shopSlot(37)
                .build();
    }

    public static VehicleUpgrade tiresSport() {
        return VehicleUpgrade.builder("sport_tires")
                .displayName("Sport Tires")
                .slot(UpgradeSlot.TIRES)
                .lore("+20% traction", "+25% lateral grip", "−10% rolling")
                .icon(Material.PAPER, 77103)
                .stats(StatModifier.builder().tractionMul(1.20).lateralGripMul(1.25).rollingResistanceMul(0.90).build())
                .craft(Material.BLACK_DYE, 2).craft(Material.LEATHER, 4).craft(Material.SLIME_BALL, 2)
                .shopPrice(Material.LEATHER, 12).shopPrice(Material.COAL, 4).shopSlot(38)
                .build();
    }

    public static VehicleUpgrade tiresOffroad() {
        return VehicleUpgrade.builder("tires_offroad")
                .displayName("Off-Road Tires")
                .slot(UpgradeSlot.TIRES)
                .lore("+35% traction", "+40% slope grip", "+rolling")
                .icon(Material.PAPER, 77111)
                .stats(StatModifier.builder()
                        .tractionMul(1.35).lateralGripMul(1.1).slopeGripMul(1.4)
                        .rollingResistanceMul(1.25).maxSpeedMul(0.95).build())
                .craft(Material.LEATHER, 4).craft(Material.SLIME_BALL, 3).craft(Material.COAL, 2)
                .shopPrice(Material.LEATHER, 16).shopPrice(Material.IRON_INGOT, 4).shopSlot(39)
                .build();
    }

    public static VehicleUpgrade tiresMud() {
        return VehicleUpgrade.builder("tires_mud")
                .displayName("Mud Tires")
                .slot(UpgradeSlot.TIRES)
                .lore("+50% traction", "+60% slope", "−12% speed")
                .icon(Material.PAPER, 77112)
                .stats(StatModifier.builder()
                        .tractionMul(1.5).slopeGripMul(1.6).lateralGripMul(0.95)
                        .maxSpeedMul(0.88).rollingResistanceMul(1.4).build())
                .craft(Material.LEATHER, 4).craft(Material.MUD, 3).craft(Material.SLIME_BALL, 2)
                .shopPrice(Material.LEATHER, 18).shopPrice(Material.COAL, 8).shopSlot(40)
                .build();
    }

    public static VehicleUpgrade tiresSlick() {
        return VehicleUpgrade.builder("tires_slick")
                .displayName("Racing Slicks")
                .slot(UpgradeSlot.TIRES)
                .lore("+40% lateral grip", "+15% speed", "−slope grip")
                .icon(Material.PAPER, 77113)
                .stats(StatModifier.builder()
                        .lateralGripMul(1.4).maxSpeedMul(1.15).tractionMul(1.1)
                        .slopeGripMul(0.7).rollingResistanceMul(0.8).build())
                .craft(Material.BLACK_DYE, 4).craft(Material.SLIME_BALL, 4).craft(Material.DIAMOND, 1)
                .shopPrice(Material.DIAMOND, 2).shopPrice(Material.LEATHER, 10).shopSlot(41)
                .build();
    }

    // ---- Wheel sizes (WHEELS slot) ----

    public static VehicleUpgrade wheelsStockPlus() {
        return VehicleUpgrade.builder("wheels_plus1")
                .displayName("Plus-1 Wheels")
                .slot(UpgradeSlot.WHEELS)
                .lore("+10% tire size", "+5% grip")
                .icon(Material.PAPER, 77114)
                .stats(StatModifier.builder().tireScaleMul(1.10).tractionMul(1.05).rideHeightAdd(0.03).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.BLACK_DYE, 2)
                .shopPrice(Material.IRON_INGOT, 6).shopSlot(42)
                .build();
    }

    public static VehicleUpgrade wheelsPlusTwo() {
        return VehicleUpgrade.builder("wheels_plus2")
                .displayName("Plus-2 Wheels")
                .slot(UpgradeSlot.WHEELS)
                .lore("+22% tire size", "+8% grip", "+ride")
                .icon(Material.PAPER, 77115)
                .stats(StatModifier.builder().tireScaleMul(1.22).tractionMul(1.08).rideHeightAdd(0.06).maxSpeedMul(0.98).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.BLACK_DYE, 2).craft(Material.COAL, 2)
                .shopPrice(Material.IRON_INGOT, 12).shopPrice(Material.COAL, 4).shopSlot(43)
                .build();
    }

    public static VehicleUpgrade wheelsDeepDish() {
        return VehicleUpgrade.builder("wheels_deep_dish")
                .displayName("Deep-Dish Wheels")
                .slot(UpgradeSlot.WHEELS)
                .lore("+15% size", "+12% turn", "street look")
                .icon(Material.PAPER, 77116)
                .stats(StatModifier.builder().tireScaleMul(1.15).turnRateMul(1.12).lateralGripMul(1.08).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.GOLD_INGOT, 1).craft(Material.BLACK_DYE, 2)
                .shopPrice(Material.GOLD_INGOT, 3).shopPrice(Material.IRON_INGOT, 8).shopSlot(44)
                .build();
    }

    public static VehicleUpgrade wheelsMonster() {
        return VehicleUpgrade.builder("wheels_monster")
                .displayName("Monster Wheels")
                .slot(UpgradeSlot.WHEELS)
                .lore("+55% tire size", "+ride", "−speed", "+slope")
                .icon(Material.PAPER, 77117)
                .stats(StatModifier.builder()
                        .tireScaleMul(1.55).rideHeightAdd(0.25).slopeGripMul(1.3)
                        .maxSpeedMul(0.85).tractionMul(1.2).rollingResistanceMul(1.35).build())
                .craft(Material.IRON_BLOCK, 1).craft(Material.BLACK_DYE, 4).craft(Material.SLIME_BLOCK, 1)
                .shopPrice(Material.IRON_BLOCK, 3).shopPrice(Material.COAL, 16).shopSlot(45)
                .build();
    }

    // ---- Lift kits (SUSPENSION slot) ----

    public static VehicleUpgrade liftTwo() {
        return VehicleUpgrade.builder("lift_2in")
                .displayName("2\" Lift Kit")
                .slot(UpgradeSlot.SUSPENSION)
                .lore("+0.15 ride height", "+8% slope grip")
                .icon(Material.PAPER, 77120)
                .stats(StatModifier.builder().rideHeightAdd(0.15).slopeGripMul(1.08).turnRateMul(0.97).build())
                .craft(Material.IRON_INGOT, 6).craft(Material.PISTON, 1)
                .shopPrice(Material.IRON_INGOT, 8).shopPrice(Material.REDSTONE, 4).shopSlot(46)
                .build();
    }

    public static VehicleUpgrade liftFour() {
        return VehicleUpgrade.builder("lift_4in")
                .displayName("4\" Lift Kit")
                .slot(UpgradeSlot.SUSPENSION)
                .lore("+0.30 ride height", "+15% slope", "−turn")
                .icon(Material.PAPER, 77121)
                .stats(StatModifier.builder().rideHeightAdd(0.30).slopeGripMul(1.15).turnRateMul(0.92).maxSpeedMul(0.97).build())
                .craft(Material.IRON_INGOT, 4).craft(Material.PISTON, 2).craft(Material.COAL, 2)
                .shopPrice(Material.IRON_INGOT, 14).shopPrice(Material.REDSTONE, 8).shopSlot(47)
                .build();
    }

    public static VehicleUpgrade liftSix() {
        return VehicleUpgrade.builder("lift_6in")
                .displayName("6\" Lift Kit")
                .slot(UpgradeSlot.SUSPENSION)
                .lore("+0.50 ride height", "+25% slope", "−handling")
                .icon(Material.PAPER, 77122)
                .stats(StatModifier.builder()
                        .rideHeightAdd(0.50).slopeGripMul(1.25).turnRateMul(0.85)
                        .lateralGripMul(0.9).maxSpeedMul(0.94).build())
                .craft(Material.IRON_BLOCK, 1).craft(Material.PISTON, 4).craft(Material.SLIME_BLOCK, 1)
                .shopPrice(Material.IRON_BLOCK, 2).shopPrice(Material.REDSTONE, 12).shopSlot(48)
                .build();
    }

    public static VehicleUpgrade liftMonster() {
        return VehicleUpgrade.builder("lift_monster")
                .displayName("Monster Lift Kit")
                .slot(UpgradeSlot.SUSPENSION)
                .lore("+0.90 ride height", "+40% slope", "crawl mode")
                .icon(Material.PAPER, 77123)
                .stats(StatModifier.builder()
                        .rideHeightAdd(0.90).slopeGripMul(1.4).turnRateMul(0.75)
                        .lateralGripMul(0.85).maxSpeedMul(0.88).tractionMul(1.15).build())
                .craft(Material.IRON_BLOCK, 2).craft(Material.PISTON, 4).craft(Material.DIAMOND, 1).craft(Material.SLIME_BLOCK, 1)
                .shopPrice(Material.DIAMOND, 2).shopPrice(Material.IRON_BLOCK, 4).shopSlot(49)
                .build();
    }
}
