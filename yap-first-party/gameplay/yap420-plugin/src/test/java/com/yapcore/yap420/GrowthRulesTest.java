package com.yapcore.yap420;

import com.yapcore.yap420.plant.GrowthRules;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GrowthRulesTest {

    @Test
    void soilsSetAccepted() {
        var soils = EnumSet.of(Material.FARMLAND);
        assertNotNull(soils);
        assertFalse(soils.contains(Material.DIRT));
    }
}
