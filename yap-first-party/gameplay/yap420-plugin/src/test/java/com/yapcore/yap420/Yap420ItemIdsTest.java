package com.yapcore.yap420;

import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.market.PackMath;
import com.yapcore.yap420.market.PackUnit;
import com.yapcore.yap420.plant.StrainId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Yap420ItemIdsTest {

    @Test
    void plantStageIds() {
        assertEquals("yap420_plant_sativa_0", Yap420ItemIds.plantStage(StrainId.SATIVA, 0));
        assertEquals("yap420_plant_indica_5", Yap420ItemIds.plantStage(StrainId.INDICA, 5));
    }

    @Test
    void seedAndBudMapping() {
        assertEquals(StrainId.SATIVA, Yap420ItemIds.strainFromSeed(Yap420ItemIds.SEED_SATIVA).orElseThrow());
        assertEquals(StrainId.INDICA, Yap420ItemIds.strainFromWetBud(Yap420ItemIds.BUD_WET_INDICA).orElseThrow());
        assertTrue(Yap420ItemIds.isConsumable(Yap420ItemIds.JOINT_SATIVA));
        assertFalse(Yap420ItemIds.isConsumable(Yap420ItemIds.SEED_SATIVA));
        assertFalse(Yap420ItemIds.isConsumable(Yap420ItemIds.OUNCE_SATIVA));
    }

    @Test
    void packIds() {
        assertEquals(Yap420ItemIds.GRAM_INDICA, Yap420ItemIds.packId(PackUnit.GRAM, StrainId.INDICA));
        assertEquals(Yap420ItemIds.BRICK_SATIVA, Yap420ItemIds.packId(PackUnit.BRICK, StrainId.SATIVA));
        assertEquals(PackUnit.OUNCE, Yap420ItemIds.packUnitOf(Yap420ItemIds.OUNCE_SATIVA).orElseThrow());
        assertEquals(PackUnit.GRAM, Yap420ItemIds.packUnitOf(Yap420ItemIds.BUD_CURED_SATIVA).orElseThrow());
        assertTrue(Yap420ItemIds.isGramSource(Yap420ItemIds.BUD_CURED_SATIVA, StrainId.SATIVA));
        assertEquals(PackUnit.BRICK, PackUnit.parse("pound").orElseThrow());
        assertEquals(PackUnit.BRICK, PackUnit.parse("lb").orElseThrow());
    }

    @Test
    void packMathLadder() {
        PackMath math = new PackMath(28, 16);
        assertEquals(28, math.gramsPerOunce());
        assertEquals(448, math.gramsPerBrick());
        assertEquals(2, math.packsFrom(PackUnit.GRAM, PackUnit.OUNCE, 56));
        assertEquals(1, math.packsFrom(PackUnit.OUNCE, PackUnit.BRICK, 16));
        assertEquals(0, math.packsFrom(PackUnit.GRAM, PackUnit.OUNCE, 27));
        assertEquals(448, math.gramsFor(PackUnit.BRICK, 1));
    }
}
