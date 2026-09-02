package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityCosts;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.EffectKind;
import com.yapcore.abilities.ProjectileSpec;
import com.yapcore.abilities.TargetMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDescribeTest {

    @Test
    void usesYamlDescriptionWhenPresent() {
        AbilityDefinition def = sample("wind_strike", "A sharp gust.", List.of(), List.of(), null);
        assertTrue(AbilityDescribe.blurb(def).contains("sharp gust"));
    }

    @Test
    void summarizesProjectileDamageAndKnockback() {
        AbilityDefinition def = sample("wind_strike", "",
                List.of(),
                List.of(
                        new AbilityEffect(EffectKind.DAMAGE, Map.of("style", "magic", "max-hit", "4")),
                        new AbilityEffect(EffectKind.KNOCKBACK, Map.of("power", "0.28"))),
                new ProjectileSpec("SNOWBALL", 1.2, 40, "CLOUD", 2, 1));
        String blurb = AbilityDescribe.blurb(def);
        assertTrue(blurb.toLowerCase().contains("projectile") || blurb.toLowerCase().contains("damage"));
        assertTrue(blurb.toLowerCase().contains("4"));
        assertTrue(blurb.toLowerCase().contains("knock"));
    }

    private static AbilityDefinition sample(
            String id,
            String description,
            List<AbilityEffect> cast,
            List<AbilityEffect> hit,
            ProjectileSpec projectile
    ) {
        return new AbilityDefinition(
                id,
                "Wind Strike",
                AbilityCategory.MAGIC,
                Map.of("magic", 1),
                new AbilityCosts(1, Map.of(), null),
                0,
                20,
                TargetMode.RAYCAST,
                "",
                List.of(),
                cast,
                hit,
                projectile,
                0,
                description);
    }
}
