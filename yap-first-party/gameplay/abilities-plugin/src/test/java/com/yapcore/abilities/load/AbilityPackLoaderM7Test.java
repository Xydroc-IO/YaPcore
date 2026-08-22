package com.yapcore.abilities.load;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityPackLoaderM7Test {

    @Test
    void loadsHomingProjectileAndConditions(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("m7.yml"), """
                abilities:
                  homing_test:
                    name: Homing Test
                    category: magic
                    icon-cmd: 78011
                    conditions:
                      - type: on_ground
                    target: raycast
                    projectile:
                      entity: SMALL_FIREBALL
                      homing: true
                      turn-rate: 0.2
                      splash-radius: 2.5
                      icon-cmd: 78011
                    on-hit:
                      - type: chain
                        jumps: 3
                        radius: 5
                        max-hit: 8
                """);

        AbilityPackLoader loader = new AbilityPackLoader();
        loader.loadDirectory(dir);
        var def = loader.get("homing_test");
        assertNotNull(def);
        assertEquals(78011, def.iconCmd());
        assertEquals(1, def.conditions().size());
        assertTrue(def.hasProjectile());
        assertTrue(def.projectile().isHoming());
        assertTrue(def.projectile().hasSplash());
    }
}
