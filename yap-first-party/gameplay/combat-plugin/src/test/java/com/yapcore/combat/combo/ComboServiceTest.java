package com.yapcore.combat.combo;

import com.yapcore.combat.CombatConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboServiceTest {

    private static ComboService service() {
        CombatConfig.ComboConfig cfg = new CombatConfig.ComboConfig(true, 3000, 20, 0.05, true);
        return new ComboService(cfg);
    }

    @Test
    void comboIncreasesDamageWithinWindow() {
        ComboService combo = service();
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ComboService.HitResult first = combo.recordHit(attacker, target, 10);
        assertEquals(10, first.damage());
        ComboService.HitResult second = combo.recordHit(attacker, target, 10);
        assertTrue(second.damage() > 10);
        assertEquals(2, second.comboCount());
    }

    @Test
    void missResetsComboWhenConfigured() {
        ComboService combo = service();
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        combo.recordHit(attacker, target, 10);
        combo.recordMiss(attacker);
        assertEquals(0, combo.currentCombo(attacker));
    }
}
