package com.yapcore.vehicles.api;

import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * A seat relative to the vehicle chassis (local space: +X right, +Y up, +Z forward).
 */
public final class VehicleSeat {

    private final String id;
    private final Vector offset;
    private final boolean driver;
    private final float yawOffset;

    public VehicleSeat(String id, Vector offset, boolean driver, float yawOffset) {
        this.id = Objects.requireNonNull(id, "id");
        this.offset = Objects.requireNonNull(offset, "offset").clone();
        this.driver = driver;
        this.yawOffset = yawOffset;
    }

    public static VehicleSeat driver(Vector offset) {
        return new VehicleSeat("driver", offset, true, 0f);
    }

    public static VehicleSeat passenger(String id, Vector offset) {
        return new VehicleSeat(id, offset, false, 0f);
    }

    public String id() {
        return id;
    }

    public Vector offset() {
        return offset.clone();
    }

    public boolean driver() {
        return driver;
    }

    public float yawOffset() {
        return yawOffset;
    }
}
