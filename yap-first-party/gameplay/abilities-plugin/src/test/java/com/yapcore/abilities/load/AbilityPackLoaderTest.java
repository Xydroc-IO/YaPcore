package com.yapcore.abilities.load;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityPackLoaderTest {

    @Test
    void loadsAbilityWithProjectileAndEffects(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.yml"), """
                abilities:
                  wind_strike:
                    name: Wind Strike
                    category: magic
                    min-level:
                      magic: 1
                    costs:
                      prayer: 1
                      runes:
                        FEATHER: 1
                    target: raycast
                    cast:
                      - type: vfx
                        particle: CLOUD
                        count: 8
                    projectile:
                      entity: SNOWBALL
                      speed: 1.2
                      max-ticks: 40
                      trail:
                        particle: CLOUD
                        count: 2
                        interval: 2
                    on-hit:
                      - type: damage
                        style: magic
                        max-hit: 4
                """);

        AbilityPackLoader loader = new AbilityPackLoader();
        loader.loadDirectory(dir);

        assertEquals(1, loader.abilities().size());
        var def = loader.get("wind_strike");
        assertNotNull(def);
        assertEquals("Wind Strike", def.displayName());
        assertTrue(def.hasProjectile());
        assertEquals(1, def.castEffects().size());
        assertEquals(1, def.hitEffects().size());
    }
}
