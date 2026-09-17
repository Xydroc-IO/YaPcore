package com.yapcore.regions.cmd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionWorldBorderFitTest {

    @Test
    void squareRegionCentersAndSizesExactly() {
        RegionWorldBorderFit fit = RegionWorldBorderFit.ofInclusive(0, 99, 0, 99);
        assertEquals(50.0, fit.centerX);
        assertEquals(50.0, fit.centerZ);
        assertEquals(100.0, fit.size);
    }

    @Test
    void nonSquareUsesLongerSide() {
        RegionWorldBorderFit fit = RegionWorldBorderFit.ofInclusive(-10, 10, 0, 4);
        assertEquals(0.5, fit.centerX);
        assertEquals(2.5, fit.centerZ);
        assertEquals(21.0, fit.size);
    }
}
