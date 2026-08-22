package com.yapcore.vehicles.upgrades;

import org.bukkit.Material;

import java.util.List;

public final class DealershipCatalog {

    private DealershipCatalog() {
    }

    public static List<DealershipListing> listings() {
        return List.of(
                DealershipListing.of("buggy", 0, Material.IRON_INGOT, 8, Material.COAL, 8),
                DealershipListing.of("truck_4x4", 1, Material.IRON_INGOT, 24, Material.COAL, 16),
                DealershipListing.of("monster_truck", 2, Material.IRON_BLOCK, 2, Material.COAL, 32),
                DealershipListing.of("sport_car", 3, Material.IRON_INGOT, 20, Material.DIAMOND, 1),
                DealershipListing.of("hypercar", 4, Material.DIAMOND, 3, Material.COAL, 24),
                DealershipListing.of("lambo", 5, Material.DIAMOND, 4, Material.EMERALD, 4),
                DealershipListing.of("ferrari", 6, Material.DIAMOND, 4, Material.REDSTONE_BLOCK, 2),
                DealershipListing.of("mclaren", 7, Material.DIAMOND, 5, Material.GOLD_INGOT, 8),
                DealershipListing.of("porsche", 8, Material.DIAMOND, 3, Material.IRON_INGOT, 16),
                DealershipListing.of("hoverbike", 9, Material.DIAMOND, 2, Material.LAPIS_LAZULI, 16),
                DealershipListing.of("chassis", 10, Material.IRON_INGOT, 4)
        );
    }

    public static DealershipListing byType(String typeId) {
        String id = typeId.toLowerCase();
        for (DealershipListing l : listings()) {
            if (l.typeId().equals(id)) {
                return l;
            }
        }
        return null;
    }
}
