package com.yapcore.vehicles.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Register / craft / shop / install vehicle upgrade parts.
 */
public interface VehicleUpgradeAPI {

    void register(VehicleUpgrade upgrade);

    boolean unregister(String upgradeId);

    Optional<VehicleUpgrade> get(String upgradeId);

    Collection<VehicleUpgrade> getAll();

    /** Create the branded inventory item for an upgrade (with CustomModelData). */
    ItemStack createItem(VehicleUpgrade upgrade);

    /** Resolve upgrade id from a held item, if any. */
    Optional<String> itemUpgradeId(ItemStack stack);

    /**
     * Install onto a live vehicle (replaces same slot). Consumes one item from player if provided.
     */
    boolean install(Vehicle vehicle, VehicleUpgrade upgrade, @Nullable Player installer);

    /** Remove upgrade from a slot; optionally give item back. */
    boolean uninstall(Vehicle vehicle, UpgradeSlot slot, boolean giveItem, @Nullable Player to);

    Map<UpgradeSlot, VehicleUpgrade> installed(Vehicle vehicle);

    /** Combined modifiers from all installed parts. */
    StatModifier combined(Vehicle vehicle);

    void openShop(Player player);
}
