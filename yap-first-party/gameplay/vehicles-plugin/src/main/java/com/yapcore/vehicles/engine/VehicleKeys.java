package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.VehiclesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** PDC keys on chassis / seat / visual entities. */
public final class VehicleKeys {

    public final NamespacedKey vehicleId;
    public final NamespacedKey typeId;
    public final NamespacedKey role;
    public final NamespacedKey seatIndex;
    public final NamespacedKey upgrades;

    public VehicleKeys(VehiclesPlugin plugin) {
        this.vehicleId = new NamespacedKey(plugin, "vehicle_id");
        this.typeId = new NamespacedKey(plugin, "type_id");
        this.role = new NamespacedKey(plugin, "role");
        this.seatIndex = new NamespacedKey(plugin, "seat_index");
        this.upgrades = new NamespacedKey(plugin, "upgrades");
    }

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Integer, Integer> INT = PersistentDataType.INTEGER;
}
