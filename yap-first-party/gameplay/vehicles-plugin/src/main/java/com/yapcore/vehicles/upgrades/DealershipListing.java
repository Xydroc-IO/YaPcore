package com.yapcore.vehicles.upgrades;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Vehicle spawn tokens sold in the garage shop. */
public record DealershipListing(String typeId, int shopSlot, List<ItemStack> price) {

    public static DealershipListing of(String typeId, int slot, Material mat, int amount) {
        return new DealershipListing(typeId, slot, List.of(new ItemStack(mat, amount)));
    }

    public static DealershipListing of(
            String typeId, int slot, Material a, int aa, Material b, int ba
    ) {
        return new DealershipListing(typeId, slot, List.of(new ItemStack(a, aa), new ItemStack(b, ba)));
    }
}
