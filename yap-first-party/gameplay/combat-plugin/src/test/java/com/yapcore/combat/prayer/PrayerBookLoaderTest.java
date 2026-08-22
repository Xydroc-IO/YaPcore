package com.yapcore.combat.prayer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrayerBookLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPrayerDefinitions() throws Exception {
        Files.writeString(tempDir.resolve("prayers.yml"), """
                prayers:
                  thick_skin:
                    name: Thick Skin
                    min-prayer-level: 1
                    drain-per-tick: 1
                    group: defence_boost
                    effect: defence_boost
                    boost: 5
                  protect_from_melee:
                    name: Protect from Melee
                    min-prayer-level: 43
                    drain-per-tick: 3
                    group: overhead
                    effect: protect_melee
                    reduction: 0.4
                """);
        PrayerBookLoader loader = new PrayerBookLoader();
        loader.load(tempDir.resolve("prayers.yml"));
        PrayerDefinition prayer = loader.get("thick_skin");
        assertNotNull(prayer);
        assertEquals(1, prayer.minPrayerLevel());
        assertEquals(5, prayer.statBoost());
        assertEquals(PrayerEffectType.DEFENCE_BOOST, prayer.effectType());
        PrayerDefinition protect = loader.get("protect_from_melee");
        assertNotNull(protect);
        assertEquals(0.4, protect.damageReduction(), 0.001);
    }
}
