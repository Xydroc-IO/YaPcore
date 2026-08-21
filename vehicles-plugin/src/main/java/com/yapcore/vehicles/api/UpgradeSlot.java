package com.yapcore.vehicles.api;

/**
 * Where an upgrade mounts — one part per slot (reinstall replaces).
 */
public enum UpgradeSlot {
    ENGINE,
    /** Tire compound: street / sport / offroad / mud / slick */
    TIRES,
    /** Tire / wheel size */
    WHEELS,
    /** Lift kits / suspension height */
    SUSPENSION,
    ARMOR,
    TANK,
    UTILITY
}
