package com.yapcore.vehicles.api;

import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named buildable chassis — BlockDisplay frame geometry + mount points.
 * <p>
 * This is <strong>not</strong> a minecart/boat. Authors start from a kit, then
 * add body panels via {@link VehicleVisual} at the named mounts (or free offsets).
 */
public final class ChassisBlueprint {

    private final String id;
    private final List<VehicleVisual> frameVisuals;
    private final List<VehicleSeat> defaultSeats;
    private final Map<String, Vector> mounts;
    private final double width;
    private final double height;
    private final double length;

    ChassisBlueprint(
            String id,
            List<VehicleVisual> frameVisuals,
            List<VehicleSeat> defaultSeats,
            Map<String, Vector> mounts,
            double width,
            double height,
            double length
    ) {
        this.id = Objects.requireNonNull(id);
        this.frameVisuals = List.copyOf(frameVisuals);
        this.defaultSeats = List.copyOf(defaultSeats);
        this.mounts = Collections.unmodifiableMap(new LinkedHashMap<>(mounts));
        this.width = width;
        this.height = height;
        this.length = length;
    }

    public String id() {
        return id;
    }

    /** Frame-only visuals (rails, cross-members, wheel hubs). */
    public List<VehicleVisual> frameVisuals() {
        return frameVisuals;
    }

    public List<VehicleSeat> defaultSeats() {
        return defaultSeats;
    }

    /**
     * Named attachment points in local space (+X right, +Y up, +Z forward).
     * Common keys: {@code hood}, {@code cabin}, {@code bed}, {@code roof},
     * {@code wheel_fl}, {@code wheel_fr}, {@code wheel_rl}, {@code wheel_rr},
     * {@code bumper_f}, {@code bumper_r}.
     */
    public Map<String, Vector> mounts() {
        return mounts;
    }

    public Vector mount(String name) {
        Vector v = mounts.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Unknown chassis mount: " + name + " on " + id);
        }
        return v.clone();
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

    /**
     * Copy of this blueprint without visuals matching the given roles
     * (e.g. drop FRAME/WHEEL when attaching a high-res ItemDisplay body).
     */
    public ChassisBlueprint withoutRoles(VehicleVisual.Role... drop) {
        java.util.Set<VehicleVisual.Role> hide = java.util.EnumSet.noneOf(VehicleVisual.Role.class);
        for (VehicleVisual.Role r : drop) {
            hide.add(r);
        }
        List<VehicleVisual> kept = frameVisuals.stream()
                .filter(v -> !hide.contains(v.role()))
                .toList();
        return new ChassisBlueprint(id + "_hd", kept, defaultSeats, mounts, width, height, length);
    }

    /** Apply frame visuals + hitbox (+ seats if the builder has none yet). */
    public VehicleType.Builder apply(VehicleType.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        for (VehicleVisual v : frameVisuals) {
            builder.visual(v);
        }
        builder.hitbox(width, height, length);
        return builder;
    }

    public VehicleType.Builder applyWithSeats(VehicleType.Builder builder) {
        apply(builder);
        for (VehicleSeat s : defaultSeats) {
            builder.seat(s);
        }
        return builder;
    }
}
