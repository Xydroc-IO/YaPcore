package com.yapcore.dungeons.gate;

import com.yapcore.dungeons.DungeonGate;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class GateEvaluator {

    private final GateTable table;

    public GateEvaluator(GateTable table) {
        this.table = table;
    }

    public DungeonGate gate(int level) {
        return table.gate(level);
    }

    public record GateResult(boolean ok, List<String> failures) {
        public static GateResult pass() {
            return new GateResult(true, List.of());
        }

        public static GateResult fail(List<String> failures) {
            return new GateResult(false, List.copyOf(failures));
        }
    }

    public CompletableFuture<GateResult> evaluate(UUID playerId, int dungeonLevel) {
        DungeonGate gate = table.gate(dungeonLevel);
        Optional<SkillService> skills = SkillServices.find();
        if (skills.isEmpty()) {
            return CompletableFuture.completedFuture(
                    GateResult.fail(List.of("YaPSkills is required for dungeon entry")));
        }
        SkillService svc = skills.get();
        return CompletableFuture.supplyAsync(() -> {
            List<String> fail = new ArrayList<>();
            try {
                int overall = svc.overallLevel(playerId).orTimeout(3, TimeUnit.SECONDS).join();
                if (overall < Math.max(10, gate.overallMin())) {
                    fail.add("Overall " + Math.max(10, gate.overallMin()) + " (have " + overall + ")");
                }
                if (gate.miningMin() > 0) {
                    int mining = svc.get(playerId, SkillId.of("mining")).orTimeout(3, TimeUnit.SECONDS).join().level();
                    if (mining < gate.miningMin()) {
                        fail.add("Mining " + gate.miningMin() + " (have " + mining + ")");
                    }
                }
                if (gate.strengthMin() > 0) {
                    int strength = svc.get(playerId, SkillId.of("strength")).orTimeout(3, TimeUnit.SECONDS).join().level();
                    if (strength < gate.strengthMin()) {
                        fail.add("Strength " + gate.strengthMin() + " (have " + strength + ")");
                    }
                }
            } catch (Exception e) {
                fail.add("Could not read skills: " + e.getMessage());
            }
            return fail.isEmpty() ? GateResult.pass() : GateResult.fail(fail);
        });
    }

    public CompletableFuture<Integer> overallLevel(UUID playerId) {
        return SkillServices.find()
                .map(svc -> svc.overallLevel(playerId))
                .orElseGet(() -> CompletableFuture.completedFuture(0));
    }
}
