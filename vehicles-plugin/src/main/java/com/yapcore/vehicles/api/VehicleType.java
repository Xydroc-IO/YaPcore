package com.yapcore.vehicles.api;

import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a vehicle kind. Plugins register these via {@link VehicleAPI}.
 * Prefer {@link ChassisKit} for the frame — do not use minecart/boat entities or items.
 */
public final class VehicleType {

    private final String id;
    private final String displayName;
    private final List<VehicleSeat> seats;
    private final List<VehicleVisual> visuals;
    private final double maxSpeed;
    private final double acceleration;
    private final double brakeForce;
    private final double reverseRatio;
    private final double turnRate;
    private final double drag;
    private final double gravity;
    private final double hoverHeight;
    private final double rideHeight;
    private final double width;
    private final double height;
    private final double length;
    private final Material spawnItem;
    private final VehicleController controller;
    private final double maxFuel;
    private final double fuelPerTick;
    private final double maxHealth;
    private final double collisionDamageScale;
    private final double traction;
    private final double lateralGrip;
    private final double rollingResistance;
    private final double yawInertia;
    private final double slopeGrip;
    private final double handbrakeGripScale;

    private VehicleType(Builder b) {
        this.id = b.id.toLowerCase();
        this.displayName = b.displayName;
        this.seats = List.copyOf(b.seats);
        this.visuals = List.copyOf(b.visuals);
        this.maxSpeed = b.maxSpeed;
        this.acceleration = b.acceleration;
        this.brakeForce = b.brakeForce;
        this.reverseRatio = b.reverseRatio;
        this.turnRate = b.turnRate;
        this.drag = b.drag;
        this.gravity = b.gravity;
        this.hoverHeight = b.hoverHeight;
        this.rideHeight = b.rideHeight;
        this.width = b.width;
        this.height = b.height;
        this.length = b.length;
        this.spawnItem = b.spawnItem;
        this.controller = b.controller;
        this.maxFuel = b.maxFuel;
        this.fuelPerTick = b.fuelPerTick;
        this.maxHealth = b.maxHealth;
        this.collisionDamageScale = b.collisionDamageScale;
        this.traction = b.traction;
        this.lateralGrip = b.lateralGrip;
        this.rollingResistance = b.rollingResistance;
        this.yawInertia = b.yawInertia;
        this.slopeGrip = b.slopeGrip;
        this.handbrakeGripScale = b.handbrakeGripScale;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<VehicleSeat> seats() {
        return seats;
    }

    public List<VehicleVisual> visuals() {
        return visuals;
    }

    public int seatCount() {
        return seats.size();
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double brakeForce() {
        return brakeForce;
    }

    public double reverseRatio() {
        return reverseRatio;
    }

    public double turnRate() {
        return turnRate;
    }

    public double drag() {
        return drag;
    }

    public double gravity() {
        return gravity;
    }

    public double hoverHeight() {
        return hoverHeight;
    }

    /** Ground clearance for wheeled vehicles (blocks above ground surface). */
    public double rideHeight() {
        return rideHeight;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double length() {
        return length;
    }

    public Material spawnItem() {
        return spawnItem;
    }

    public VehicleController controller() {
        return controller;
    }

    public double maxFuel() {
        return maxFuel;
    }

    public double fuelPerTick() {
        return fuelPerTick;
    }

    public boolean usesFuel() {
        return maxFuel > 0 && fuelPerTick > 0;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public boolean usesDamage() {
        return maxHealth > 0;
    }

    public double collisionDamageScale() {
        return collisionDamageScale;
    }

    /** Base tire grip multiplier (combined with surface). */
    public double traction() {
        return traction;
    }

    /** How quickly lateral slip is killed (higher = less drift). */
    public double lateralGrip() {
        return lateralGrip;
    }

    /** Constant rolling resistance (not vanilla). */
    public double rollingResistance() {
        return rollingResistance;
    }

    /** Yaw-rate smoothing — higher = heavier steering feel. */
    public double yawInertia() {
        return yawInertia;
    }

    /** How much slope grade affects longitudinal speed. */
    public double slopeGrip() {
        return slopeGrip;
    }

    /** Lateral grip scale while handbrake is held (0–1). */
    public double handbrakeGripScale() {
        return handbrakeGripScale;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private final List<VehicleSeat> seats = new ArrayList<>();
        private final List<VehicleVisual> visuals = new ArrayList<>();
        private double maxSpeed = 0.55;
        private double acceleration = 0.035;
        private double brakeForce = 0.06;
        private double reverseRatio = 0.45;
        private double turnRate = 4.5;
        private double drag = 0.012;
        private double gravity = 0.08;
        private double hoverHeight = 0.0;
        private double rideHeight = 0.05;
        private double width = 1.4;
        private double height = 1.0;
        private double length = 2.2;
        private Material spawnItem = ChassisKit.SPAWN_TOKEN;
        private VehicleController controller = VehicleController.DEFAULT;
        private double maxFuel = 1000;
        private double fuelPerTick = 0.35;
        private double maxHealth = 40;
        private double collisionDamageScale = 8.0;
        private double traction = 1.0;
        private double lateralGrip = 0.28;
        private double rollingResistance = 0.008;
        private double yawInertia = 0.35;
        private double slopeGrip = 0.045;
        private double handbrakeGripScale = 0.25;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = id;
        }

        public Builder displayName(String name) {
            this.displayName = Objects.requireNonNull(name);
            return this;
        }

        /**
         * Apply a YaP {@link ChassisBlueprint} frame (and optional default seats).
         * Prefer this over minecart/boat visuals.
         */
        public Builder chassis(ChassisBlueprint blueprint) {
            return chassis(blueprint, true);
        }

        public Builder chassis(ChassisBlueprint blueprint, boolean includeDefaultSeats) {
            Objects.requireNonNull(blueprint, "blueprint");
            blueprint.apply(this);
            if (includeDefaultSeats && seats.isEmpty()) {
                for (VehicleSeat s : blueprint.defaultSeats()) {
                    seat(s);
                }
            }
            return this;
        }

        public Builder seat(VehicleSeat seat) {
            this.seats.add(Objects.requireNonNull(seat));
            return this;
        }

        public Builder seats(VehicleSeat... seats) {
            for (VehicleSeat s : seats) {
                seat(s);
            }
            return this;
        }

        public Builder visual(VehicleVisual visual) {
            this.visuals.add(Objects.requireNonNull(visual));
            return this;
        }

        public Builder visuals(VehicleVisual... visuals) {
            for (VehicleVisual v : visuals) {
                visual(v);
            }
            return this;
        }

        /** Body panel at a named chassis mount (after {@link #chassis}). */
        public Builder bodyPanel(ChassisBlueprint blueprint, String mount, Material mat, double sx, double sy, double sz) {
            Vector off = blueprint.mount(mount);
            return visual(VehicleVisual.block(mat).offset(off).scale(sx, sy, sz).build());
        }

        public Builder bodyItem(ChassisBlueprint blueprint, String mount, Material mat, double scale) {
            Vector off = blueprint.mount(mount);
            return visual(VehicleVisual.item(mat).offset(off).scale(scale).build());
        }

        /**
         * High-res ItemDisplay model from the YaP Vehicles resource pack
         * ({@code CustomModelData} on paper — see {@code resourcepacks/yap-vehicles}).
         */
        public Builder bodyModel(int customModelData, double x, double y, double z, double scale) {
            return visual(VehicleVisual.item(ChassisKit.SPAWN_TOKEN)
                    .offset(x, y, z)
                    .scale(scale)
                    .customModelData(customModelData)
                    .role(VehicleVisual.Role.FRAME)
                    .yawOffset(180f)
                    .build());
        }

        public Builder bodyModel(ChassisBlueprint blueprint, String mount, int customModelData, double scale) {
            Vector off = blueprint.mount(mount);
            return bodyModel(customModelData, off.getX(), off.getY(), off.getZ(), scale);
        }

        public Builder maxSpeed(double v) {
            this.maxSpeed = v;
            return this;
        }

        public Builder acceleration(double v) {
            this.acceleration = v;
            return this;
        }

        public Builder brakeForce(double v) {
            this.brakeForce = v;
            return this;
        }

        public Builder reverseRatio(double v) {
            this.reverseRatio = v;
            return this;
        }

        public Builder turnRate(double degPerTick) {
            this.turnRate = degPerTick;
            return this;
        }

        public Builder drag(double v) {
            this.drag = v;
            return this;
        }

        public Builder gravity(double v) {
            this.gravity = v;
            return this;
        }

        public Builder hoverHeight(double v) {
            this.hoverHeight = v;
            return this;
        }

        /** Wheeled ground clearance (default 0.05). Monster trucks use ~1.0+. */
        public Builder rideHeight(double v) {
            this.rideHeight = Math.max(0, v);
            return this;
        }

        public Builder hitbox(double width, double height, double length) {
            this.width = width;
            this.height = height;
            this.length = length;
            return this;
        }

        /**
         * Spawn token material. Defaults to {@link ChassisKit#SPAWN_TOKEN} (paper).
         * Do not use {@link Material#MINECART} / boats — they imply vanilla vehicles.
         */
        public Builder spawnItem(Material material) {
            this.spawnItem = Objects.requireNonNull(material);
            if (isVanillaVehicleItem(material)) {
                throw new IllegalArgumentException(
                        "spawnItem must not be a minecart/boat — use ChassisKit.SPAWN_TOKEN or another non-vehicle item");
            }
            return this;
        }

        public Builder controller(VehicleController controller) {
            this.controller = Objects.requireNonNull(controller);
            return this;
        }

        public Builder fuel(double maxFuel, double fuelPerTick) {
            this.maxFuel = maxFuel;
            this.fuelPerTick = fuelPerTick;
            return this;
        }

        public Builder noFuel() {
            this.maxFuel = 0;
            this.fuelPerTick = 0;
            return this;
        }

        public Builder health(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        public Builder invulnerable() {
            this.maxHealth = 0;
            return this;
        }

        public Builder collisionDamageScale(double scale) {
            this.collisionDamageScale = scale;
            return this;
        }

        public Builder traction(double v) {
            this.traction = v;
            return this;
        }

        public Builder lateralGrip(double v) {
            this.lateralGrip = v;
            return this;
        }

        public Builder rollingResistance(double v) {
            this.rollingResistance = v;
            return this;
        }

        public Builder yawInertia(double v) {
            this.yawInertia = Math.max(0.05, v);
            return this;
        }

        public Builder slopeGrip(double v) {
            this.slopeGrip = v;
            return this;
        }

        public Builder handbrakeGripScale(double v) {
            this.handbrakeGripScale = Math.max(0, Math.min(1, v));
            return this;
        }

        public VehicleType build() {
            if (seats.isEmpty()) {
                seats.add(VehicleSeat.driver(new Vector(0, 0.35, 0.1)));
            }
            long drivers = seats.stream().filter(VehicleSeat::driver).count();
            if (drivers != 1) {
                throw new IllegalStateException("VehicleType '" + id + "' must have exactly one driver seat");
            }
            if (visuals.isEmpty()) {
                // Ensure every type has a visible YaP frame
                ChassisKit.bare().apply(this);
            }
            return new VehicleType(this);
        }

        private static boolean isVanillaVehicleItem(Material m) {
            String n = m.name();
            return n.contains("MINECART") || n.contains("BOAT") || n.equals("RAIL")
                    || n.contains("_RAIL") || n.equals("SADDLE");
        }
    }
}
