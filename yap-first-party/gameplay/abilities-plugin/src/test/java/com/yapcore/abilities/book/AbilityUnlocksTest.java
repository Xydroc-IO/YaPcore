package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityCosts;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.TargetMode;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityUnlocksTest {

    @Test
    void emptyRequirementsAlwaysUnlocked() {
        AbilityDefinition def = sample("free_spell", Map.of());
        assertTrue(AbilityUnlocks.isUnlocked(null, def, List.of()));
    }

    @Test
    void respectsSkillGate() {
        UUID id = UUID.randomUUID();
        AbilityDefinition def = sample("wind", Map.of("magic", 10));
        List<SkillProgress> low = List.of(new SkillProgress(id, SkillId.of("magic"), 0, 5));
        List<SkillProgress> high = List.of(new SkillProgress(id, SkillId.of("magic"), 0, 12));
        assertFalse(AbilityUnlocks.isUnlocked(null, def, low));
        assertTrue(AbilityUnlocks.isUnlocked(null, def, high));
    }

    private static AbilityDefinition sample(String id, Map<String, Integer> mins) {
        return new AbilityDefinition(
                id,
                id,
                AbilityCategory.MAGIC,
                mins,
                new AbilityCosts(0, Map.of(), null),
                0,
                20,
                TargetMode.SELF,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                100);
    }
}
