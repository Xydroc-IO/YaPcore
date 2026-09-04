package com.yapcore.abilities.load;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V4 soak gate (offline): regenerated packs stay within Folia-safe authoring budgets
 * and hero showcase uses distinct V1 choreography (not burst-only).
 */
class AbilityVfxSoakGateTest {

    private static final Set<String> ALLOWED_SHAPES = Set.of(
            "burst", "ring", "helix", "spiral", "beam", "nova",
            "cone", "pillar", "column", "orb", "sphere", "shockwave", "wave");

    @Test
    void generatedPacksStayWithinVfxBudgets(@TempDir Path dir) throws Exception {
        Path src = Path.of("src/main/resources/abilities");
        if (!Files.isDirectory(src)) {
            src = Path.of("yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities");
        }
        assertTrue(Files.isDirectory(src), "abilities resources missing");

        int abilities = 0;
        int maxCastSteps = 0;
        int maxHitSteps = 0;
        int timedSteps = 0;
        int shakeSteps = 0;
        Set<String> shapes = new HashSet<>();

        try (var stream = Files.list(src)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                String name = file.getFileName().toString();
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                var section = yaml.getConfigurationSection("abilities");
                if (section == null) {
                    continue;
                }
                for (String id : section.getKeys(false)) {
                    abilities++;
                    var ability = section.getConfigurationSection(id);
                    if (ability == null) {
                        continue;
                    }
                    List<Map<?, ?>> cast = ability.getMapList("cast");
                    List<Map<?, ?>> hit = ability.getMapList("on-hit");
                    maxCastSteps = Math.max(maxCastSteps, cast.size());
                    maxHitSteps = Math.max(maxHitSteps, hit.size());
                    for (Map<?, ?> step : cast) {
                        inspect(step, shapes);
                        if (step.containsKey("at")) {
                            timedSteps++;
                        }
                        if ("shake".equals(String.valueOf(step.get("type")))) {
                            shakeSteps++;
                        }
                    }
                    for (Map<?, ?> step : hit) {
                        inspect(step, shapes);
                        if (step.containsKey("at")) {
                            timedSteps++;
                        }
                        if ("shake".equals(String.valueOf(step.get("type")))) {
                            shakeSteps++;
                        }
                    }
                    var proj = ability.getConfigurationSection("projectile");
                    if (proj != null) {
                        // Splash must stay within author guidance (config comment ≤ 6).
                        assertTrue(proj.getDouble("splash-radius", 0) <= 6.0,
                                id + " splash-radius too large for Folia guidance");
                    }
                    if (!name.startsWith("showcase_")) {
                        // Bulk packs: avoid runaway cast lists that spam region schedulers.
                        assertTrue(cast.size() <= 12, id + " cast list too long: " + cast.size());
                        assertTrue(hit.size() <= 12, id + " on-hit list too long: " + hit.size());
                    }
                }
            }
        }

        assertTrue(abilities >= 230, "expected full ability catalog, got " + abilities);
        assertTrue(timedSteps >= 200, "V2 kits should emit many timed at: steps, got " + timedSteps);
        assertTrue(shakeSteps >= 100, "expected widespread shake usage, got " + shakeSteps);
        assertTrue(shapes.contains("shockwave"));
        assertTrue(shapes.contains("orb") || shapes.contains("pillar") || shapes.contains("cone"));
        assertTrue(maxCastSteps > 0 && maxHitSteps > 0);
        for (String shape : shapes) {
            assertTrue(ALLOWED_SHAPES.contains(shape), "unknown shape: " + shape);
        }
    }

    @Test
    void heroShowcaseHasDistinctChoreography() throws Exception {
        Path file = Path.of("src/main/resources/abilities/showcase_heroes.yml");
        if (!Files.isRegularFile(file)) {
            file = Path.of("yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities/showcase_heroes.yml");
        }
        assertTrue(Files.isRegularFile(file));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var section = yaml.getConfigurationSection("abilities");
        assertTrue(section != null);
        assertEquals(12, section.getKeys(false).size());

        for (String id : section.getKeys(false)) {
            var ability = section.getConfigurationSection(id);
            assertTrue(ability != null);
            List<Map<?, ?>> cast = ability.getMapList("cast");
            long vfx = cast.stream().filter(s -> "vfx".equals(String.valueOf(s.get("type")))).count();
            assertTrue(vfx >= 2, id + " should layer multiple cast VFX");
            boolean hasAdvancedShape = cast.stream().anyMatch(s -> {
                String shape = String.valueOf(s.get("shape"));
                return Set.of("cone", "pillar", "orb", "shockwave", "helix", "beam").contains(shape);
            });
            assertTrue(hasAdvancedShape, id + " missing advanced cast shape");
            // Reject trivial burst-only heroes
            boolean onlyBurst = cast.stream()
                    .filter(s -> "vfx".equals(String.valueOf(s.get("type"))))
                    .allMatch(s -> "burst".equals(String.valueOf(s.get("shape"))));
            assertFalse(onlyBurst, id + " is burst-only");
        }
    }

    private static void inspect(Map<?, ?> step, Set<String> shapes) {
        if (!"vfx".equals(String.valueOf(step.get("type")))) {
            return;
        }
        Object shape = step.get("shape");
        if (shape != null) {
            shapes.add(String.valueOf(shape).toLowerCase());
        }
    }
}
