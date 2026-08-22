package com.yapcore.combat.projectile;

import com.yapcore.combat.CombatConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectilePhysicsTest {

    @Test
    void pierceBonusScalesWithRangedLevel() {
        CombatConfig.ProjectileConfig cfg = new CombatConfig.ProjectileConfig(
                true, 1.0, 1.0, 25, true, 0.004, 0.55, 1.25);
        assertEquals(0, ProjectilePhysics.pierceBonus(10, cfg));
        assertEquals(2, ProjectilePhysics.pierceBonus(50, cfg));
    }
}
