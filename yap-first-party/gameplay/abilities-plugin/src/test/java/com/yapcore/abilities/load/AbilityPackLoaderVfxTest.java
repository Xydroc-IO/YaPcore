package com.yapcore.abilities.load;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityPackLoaderVfxTest {

    @Test
    void loadsV1PathTrailAndImpactFields(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("vfx.yml"), """
                abilities:
                  arc_test:
                    name: Arc Test
                    category: magic
                    target: raycast
                    cast:
                      - type: vfx
                        at: 4
                        shape: shockwave
                        particle: FLAME
                      - type: shake
                        power: 0.2
                    projectile:
                      entity: SNOWBALL
                      path: arc
                      arc-height: 2.5
                      impact-shake: true
                      shake-power: 0.18
                      trail:
                        particle: FLAME
                        count: 6
                        interval: 1
                        style: motion
                        falloff: 0.55
                """);

        AbilityPackLoader loader = new AbilityPackLoader();
        loader.loadDirectory(dir);
        var def = loader.get("arc_test");
        assertNotNull(def);
        assertTrue(def.hasProjectile());
        assertTrue(def.projectile().isArc());
        assertEquals(2.5, def.projectile().arcHeight(), 0.001);
        assertEquals("motion", def.projectile().trailStyle());
        assertEquals(0.55, def.projectile().trailFalloff(), 0.001);
        assertTrue(def.projectile().impactShake());
        assertEquals(0.18, def.projectile().shakePower(), 0.001);
        assertTrue(def.projectile().hasMotionTrail());
        assertEquals(2, def.castEffects().size());
        assertEquals("4", def.castEffects().get(0).param("at"));
        assertEquals(com.yapcore.abilities.EffectKind.SHAKE, def.castEffects().get(1).kind());
    }

    @Test
    void defaultsRemainStraightBurstWithoutV1Fields(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("legacy.yml"), """
                abilities:
                  plain_bolt:
                    name: Plain Bolt
                    category: magic
                    target: raycast
                    projectile:
                      entity: SNOWBALL
                      trail:
                        particle: CLOUD
                        count: 3
                """);

        AbilityPackLoader loader = new AbilityPackLoader();
        loader.loadDirectory(dir);
        var def = loader.get("plain_bolt");
        assertNotNull(def);
        assertFalse(def.projectile().isArc());
        assertEquals("straight", def.projectile().path());
        assertEquals("burst", def.projectile().trailStyle());
        assertFalse(def.projectile().impactShake());
    }
}
