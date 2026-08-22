package com.yapcore.vehicles.api;

import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for YaP chassis kits — frames with glass windshields and cabin interiors.
 */
public final class ChassisKit {

    public static final Material SPAWN_TOKEN = Material.PAPER;

    private ChassisKit() {
    }

    public static ChassisBlueprint bare() {
        return bare(2.2, 1.4, 0.9, 0.35);
    }

    public static ChassisBlueprint bare(double length, double width, double height) {
        return bare(length, width, height, 0.35);
    }

    public static ChassisBlueprint bare(double length, double width, double height, double wheelSize) {
        double halfL = length * 0.5;
        double halfW = width * 0.5;
        double railY = height * 0.35;
        double wheelY = height * 0.18;

        List<VehicleVisual> frame = new ArrayList<>();
        frame.add(block(Material.IRON_BLOCK, 0, railY, 0, width * 0.15, height * 0.12, length * 0.95));
        frame.add(block(Material.IRON_BLOCK, 0, railY, halfL * 0.7, width * 0.9, height * 0.1, length * 0.12));
        frame.add(block(Material.IRON_BLOCK, 0, railY, -halfL * 0.7, width * 0.9, height * 0.1, length * 0.12));
        frame.add(block(Material.GRAY_CONCRETE, 0, railY + 0.05, 0, width * 0.2, height * 0.08, length * 0.7));
        addWheels(frame, halfW, wheelY, halfL, wheelSize);

        Map<String, Vector> mounts = baseMounts(halfL, halfW, height, wheelY);
        List<VehicleSeat> seats = List.of(VehicleSeat.driver(mounts.get("driver").clone()));
        return new ChassisBlueprint("bare", frame, seats, mounts, width, height, length);
    }

    /** Street / sport car: cabin, clear glass, full interior. */
    public static ChassisBlueprint car() {
        return car(2.4, 1.6, 1.1, 0.38, false);
    }

    /** Low sport coupe. */
    public static ChassisBlueprint sport() {
        return car(2.5, 1.7, 0.95, 0.40, true);
    }

    /** Ultra-low hyper / exotic. */
    public static ChassisBlueprint hyper() {
        return car(2.6, 1.85, 0.85, 0.42, true);
    }

    /** Mid-size 4x4 pickup / SUV chassis (taller). */
    public static ChassisBlueprint fourByFour() {
        double length = 3.0;
        double width = 1.9;
        double height = 1.55;
        double wheel = 0.55;
        ChassisBlueprint bare = bare(length, width, height, wheel);
        List<VehicleVisual> extras = new ArrayList<>(bare.frameVisuals());
        addCabinShell(extras, 1.5, 0.95, 1.3, 0.15, 1.05, 0.85);
        addGlass(extras, 1.35, 1.25, 0.95, 1.15, 0.55);
        addInterior(extras, 1.2, 0.55, 0.7, 0.75);
        Map<String, Vector> mounts = new LinkedHashMap<>(bare.mounts());
        mounts.put("cabin", new Vector(0, 1.1, 0.7));
        mounts.put("driver", new Vector(0.25, 0.7, 0.75));
        mounts.put("dash", new Vector(0, 0.85, 1.05));
        List<VehicleSeat> seats = List.of(
                VehicleSeat.driver(new Vector(0.25, 0.7, 0.75)),
                VehicleSeat.passenger("shotgun", new Vector(-0.25, 0.7, 0.75)),
                VehicleSeat.passenger("rear", new Vector(0, 0.7, 0.05)),
                VehicleSeat.passenger("bed", new Vector(0, 0.85, -0.9))
        );
        return new ChassisBlueprint("4x4", extras, seats, mounts, width, height, length);
    }

    /** Tall monster truck — huge wheels, high cabin. */
    public static ChassisBlueprint monster() {
        double length = 2.8;
        double width = 2.2;
        double height = 2.0;
        double wheel = 0.95;
        ChassisBlueprint bare = bare(length, width, height, wheel);
        List<VehicleVisual> extras = new ArrayList<>(bare.frameVisuals());
        // Lifted cabin
        extras.add(block(Material.YELLOW_CONCRETE, 0, 1.35, 0.35, 1.6, 0.85, 1.4));
        addGlass(extras, 1.4, 1.7, 0.85, 1.2, 0.9);
        addInterior(extras, 1.2, 1.05, 0.4, 1.15);
        Map<String, Vector> mounts = new LinkedHashMap<>(bare.mounts());
        mounts.put("cabin", new Vector(0, 1.4, 0.3));
        mounts.put("driver", new Vector(0.2, 1.15, 0.4));
        mounts.put("dash", new Vector(0, 1.3, 0.75));
        List<VehicleSeat> seats = List.of(
                VehicleSeat.driver(new Vector(0.2, 1.15, 0.4)),
                VehicleSeat.passenger("shotgun", new Vector(-0.2, 1.15, 0.4))
        );
        return new ChassisBlueprint("monster", extras, seats, mounts, width, height, length);
    }

    public static ChassisBlueprint truck() {
        ChassisBlueprint bare = bare(3.4, 2.0, 1.4, 0.45);
        List<VehicleVisual> extras = new ArrayList<>(bare.frameVisuals());
        extras.add(block(Material.ORANGE_CONCRETE, 0, 0.95, 1.0, 1.6, 0.9, 1.2));
        extras.add(block(Material.GRAY_CONCRETE, 0, 0.7, -0.6, 1.7, 0.5, 1.6));
        addGlass(extras, 1.4, 1.25, 1.35, 1.1, 1.15);
        addInterior(extras, 1.2, 0.6, 0.95, 0.85);
        Map<String, Vector> mounts = new LinkedHashMap<>(bare.mounts());
        mounts.put("cabin", new Vector(0, 1.0, 0.9));
        mounts.put("bed", new Vector(0, 0.85, -0.7));
        mounts.put("driver", new Vector(0.25, 0.65, 0.95));
        mounts.put("dash", new Vector(0, 0.85, 1.25));
        List<VehicleSeat> seats = List.of(
                VehicleSeat.driver(new Vector(0.25, 0.65, 0.95)),
                VehicleSeat.passenger("mid", new Vector(-0.25, 0.65, 0.95)),
                VehicleSeat.passenger("bed", new Vector(0, 0.75, -1.0))
        );
        return new ChassisBlueprint("truck", extras, seats, mounts, 2.0, 1.4, 3.4);
    }

    public static ChassisBlueprint bike() {
        double length = 1.8;
        double width = 0.9;
        double height = 0.85;
        List<VehicleVisual> frame = new ArrayList<>();
        frame.add(block(Material.IRON_BLOCK, 0, 0.35, 0, 0.25, 0.12, length * 0.9));
        frame.add(block(Material.GRAY_CONCRETE, 0, 0.5, 0.2, 0.35, 0.2, 0.6));
        frame.add(wheel(0, 0.2, 0.65, 0.4));
        frame.add(wheel(0, 0.2, -0.55, 0.4));
        Map<String, Vector> mounts = new LinkedHashMap<>();
        mounts.put("origin", new Vector(0, 0, 0));
        mounts.put("cabin", new Vector(0, 0.55, 0.1));
        mounts.put("hood", new Vector(0, 0.45, 0.7));
        mounts.put("bed", new Vector(0, 0.4, -0.5));
        mounts.put("driver", new Vector(0, 0.45, 0.05));
        mounts.put("wheel_fl", new Vector(0, 0.2, 0.65));
        mounts.put("wheel_rl", new Vector(0, 0.2, -0.55));
        List<VehicleSeat> seats = List.of(VehicleSeat.driver(mounts.get("driver").clone()));
        return new ChassisBlueprint("bike", frame, seats, mounts, width, height, length);
    }

    // -------------------------------------------------------------------------

    private static ChassisBlueprint car(
            double length, double width, double height, double wheelSize, boolean low
    ) {
        ChassisBlueprint bare = bare(length, width, height, wheelSize);
        List<VehicleVisual> extras = new ArrayList<>(bare.frameVisuals());
        double cabinY = low ? height * 0.72 : 0.85;
        double cabinH = low ? 0.42 : 0.55;
        addCabinShell(extras, width * 0.82, cabinY, length * 0.42, cabinH, 0.1, length * 0.45);
        addGlass(extras, width * 0.72, cabinY + cabinH * 0.55, length * 0.28,
                width * 0.65, cabinY + cabinH * 0.35);
        addInterior(extras, width * 0.65, height * 0.42, length * 0.12, cabinY * 0.85);
        Map<String, Vector> mounts = new LinkedHashMap<>(bare.mounts());
        mounts.put("cabin", new Vector(0, cabinY, 0.1));
        mounts.put("dash", new Vector(0, cabinY + 0.05, length * 0.22));
        mounts.put("driver", new Vector(0.28, height * 0.4, length * 0.12));
        List<VehicleSeat> seats = List.of(
                VehicleSeat.driver(new Vector(0.28, height * 0.4, length * 0.12)),
                VehicleSeat.passenger("shotgun", new Vector(-0.28, height * 0.4, length * 0.12)),
                VehicleSeat.passenger("rear", new Vector(0, height * 0.4, -length * 0.28))
        );
        String id = low ? "sport" : "car";
        return new ChassisBlueprint(id, extras, seats, mounts, width, height, length);
    }

    private static void addCabinShell(
            List<VehicleVisual> out,
            double w, double y, double z, double h, double zCenter, double depth
    ) {
        out.add(block(Material.LIGHT_GRAY_CONCRETE, 0, y, zCenter, w, h, depth));
        // roof
        out.add(block(Material.GRAY_CONCRETE, 0, y + h * 0.55, zCenter - 0.05, w * 0.95, 0.08, depth * 0.85));
    }

    /** Clear glass windshield + side windows (see-through). */
    private static void addGlass(
            List<VehicleVisual> out,
            double frontW, double frontY, double frontZ,
            double sideW, double sideY
    ) {
        // Front windshield — thin clear glass
        out.add(glass(0, frontY, frontZ, frontW, 0.42, 0.06));
        // Rear window
        out.add(glass(0, frontY - 0.05, -frontZ * 0.85, frontW * 0.9, 0.35, 0.05));
        // Side windows
        out.add(glass(sideW * 0.52, sideY, 0.05, 0.05, 0.38, 0.7));
        out.add(glass(-sideW * 0.52, sideY, 0.05, 0.05, 0.38, 0.7));
    }

    /** Seats, dash, wheel, floor — visible through glass. */
    private static void addInterior(
            List<VehicleVisual> out,
            double cabinW, double seatY, double seatZ, double dashY
    ) {
        // Floor
        out.add(interior(Material.GRAY_CONCRETE, 0, seatY - 0.12, seatZ, cabinW * 0.9, 0.06, 1.0));
        // Driver + passenger seats
        out.add(interior(Material.BLACK_WOOL, 0.28, seatY, seatZ, 0.35, 0.22, 0.4));
        out.add(interior(Material.BLACK_WOOL, -0.28, seatY, seatZ, 0.35, 0.22, 0.4));
        // Seat backs
        out.add(interior(Material.BLACK_WOOL, 0.28, seatY + 0.22, seatZ - 0.12, 0.35, 0.35, 0.1));
        out.add(interior(Material.BLACK_WOOL, -0.28, seatY + 0.22, seatZ - 0.12, 0.35, 0.35, 0.1));
        // Rear bench
        out.add(interior(Material.BLACK_WOOL, 0, seatY, seatZ - 0.55, cabinW * 0.75, 0.2, 0.35));
        // Dashboard
        out.add(interior(Material.SMOOTH_STONE, 0, dashY, seatZ + 0.35, cabinW * 0.85, 0.18, 0.25));
        // Steering wheel (item)
        out.add(VehicleVisual.item(Material.TRIPWIRE_HOOK)
                .offset(0.28, dashY + 0.05, seatZ + 0.22)
                .scale(0.45)
                .role(VehicleVisual.Role.INTERIOR)
                .build());
        // Center console
        out.add(interior(Material.POLISHED_BLACKSTONE, 0, seatY + 0.05, seatZ + 0.1, 0.18, 0.15, 0.5));
    }

    private static void addWheels(
            List<VehicleVisual> frame, double halfW, double wheelY, double halfL, double size
    ) {
        frame.add(wheel(halfW * 0.85, wheelY, halfL * 0.65, size));
        frame.add(wheel(-halfW * 0.85, wheelY, halfL * 0.65, size));
        frame.add(wheel(halfW * 0.85, wheelY, -halfL * 0.65, size));
        frame.add(wheel(-halfW * 0.85, wheelY, -halfL * 0.65, size));
    }

    private static Map<String, Vector> baseMounts(
            double halfL, double halfW, double height, double wheelY
    ) {
        Map<String, Vector> mounts = new LinkedHashMap<>();
        mounts.put("origin", new Vector(0, 0, 0));
        mounts.put("hood", new Vector(0, height * 0.55, halfL * 0.55));
        mounts.put("cabin", new Vector(0, height * 0.7, halfL * 0.1));
        mounts.put("roof", new Vector(0, height * 1.05, 0));
        mounts.put("bed", new Vector(0, height * 0.55, -halfL * 0.45));
        mounts.put("bumper_f", new Vector(0, height * 0.3, halfL * 0.95));
        mounts.put("bumper_r", new Vector(0, height * 0.3, -halfL * 0.95));
        mounts.put("wheel_fl", new Vector(halfW * 0.85, wheelY, halfL * 0.65));
        mounts.put("wheel_fr", new Vector(-halfW * 0.85, wheelY, halfL * 0.65));
        mounts.put("wheel_rl", new Vector(halfW * 0.85, wheelY, -halfL * 0.65));
        mounts.put("wheel_rr", new Vector(-halfW * 0.85, wheelY, -halfL * 0.65));
        mounts.put("driver", new Vector(0, height * 0.45, halfL * 0.15));
        mounts.put("dash", new Vector(0, height * 0.55, halfL * 0.35));
        return mounts;
    }

    private static VehicleVisual block(
            Material mat, double x, double y, double z, double sx, double sy, double sz
    ) {
        return VehicleVisual.block(mat).offset(x, y, z).scale(sx, sy, sz)
                .role(VehicleVisual.Role.FRAME).build();
    }

    private static VehicleVisual wheel(double x, double y, double z, double size) {
        return VehicleVisual.block(Material.BLACK_CONCRETE)
                .offset(x, y, z).scale(size, size, size * 0.85)
                .role(VehicleVisual.Role.WHEEL).build();
    }

    private static VehicleVisual glass(
            double x, double y, double z, double sx, double sy, double sz
    ) {
        return VehicleVisual.block(Material.GLASS)
                .offset(x, y, z).scale(sx, sy, sz)
                .role(VehicleVisual.Role.GLASS)
                .customize(d -> d.setBrightness(new Display.Brightness(15, 15)))
                .build();
    }

    private static VehicleVisual interior(
            Material mat, double x, double y, double z, double sx, double sy, double sz
    ) {
        return VehicleVisual.block(mat).offset(x, y, z).scale(sx, sy, sz)
                .role(VehicleVisual.Role.INTERIOR).build();
    }
}
