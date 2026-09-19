package com.yapcore.bench;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JSON cite report. Split from {@link BenchSampler} for the 500-line domain gate. */
final class BenchSamplerReport {

    private BenchSamplerReport() {
    }

    static void write(JavaPlugin plugin, String msptAggregation, int sampleCxWest, int sampleCxEast,
                      String scenario, String label, String outPath,
                      int warmup, int sampleSec, List<Double> mspt, List<Double> tps,
                      int expectedTnt, BenchSampler.LoadSnapshot start, BenchSampler.LoadSnapshot end,
                      int saveAllAt, int saveFiredAt) {
        double mean = mean(mspt);
        double p50 = percentile(mspt, 0.50);
        double p95 = percentile(mspt, 0.95);
        double tpsMean = mean(tps);
        double msptPreSave = Double.NaN;
        double msptSaveSpike = Double.NaN;
        if (saveFiredAt >= 0 && saveFiredAt < mspt.size()) {
            List<Double> before = mspt.subList(0, Math.max(0, saveFiredAt));
            int endIdx = Math.min(mspt.size(), saveFiredAt + 8);
            List<Double> after = mspt.subList(saveFiredAt, endIdx);
            if (!before.isEmpty()) {
                msptPreSave = mean(before);
            }
            if (!after.isEmpty()) {
                double max = after.getFirst();
                for (double d : after) {
                    if (d > max) {
                        max = d;
                    }
                }
                msptSaveSpike = max;
            }
        }
        double fuseDrop = start.fuseMean() - end.fuseMean();
        double expectedFuseDrop = sampleSec * 20.0;
        boolean fuseOk = start.tntAlive() == 0
                || (fuseDrop >= expectedFuseDrop * 0.75 && end.tntAlive() >= start.tntAlive() * 0.98);
        int targetPlayers = Integer.getInteger("yap.bench.players", 0);
        int needHold = Math.max(1, (int) Math.ceil(targetPlayers * 0.90));
        boolean playersOk = (!"highpop".equals(scenario) && !"fullcite".equals(scenario))
                || (targetPlayers <= 0)
                || (start.players() >= needHold && end.players() >= needHold);
        String botLoad = System.getProperty("yap.bench.bot_load", "active");
        if (botLoad == null || botLoad.isBlank()) {
            botLoad = "active";
        }
        String measurementScope = System.getProperty("yap.bench.measurement_scope", "game_tick_mspt");
        String tickModel = System.getProperty("yap.bench.tick_model", "regionized");
        if (tickModel == null || tickModel.isBlank()) {
            tickModel = "regionized";
        }
        String gameXms = System.getProperty("yap.bench.game_xms", "");
        String gameXmx = System.getProperty("yap.bench.game_xmx", "");
        long gameJvmMaxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean chassisPresent = Boolean.parseBoolean(System.getProperty("yap.bench.chassis_present", "false"));
        BenchKnobSnapshot knobs = BenchKnobSnapshot.capture();
        String json = """
                {
                  "label": %s,
                  "scenario": %s,
                  "warmup_seconds": %d,
                  "sample_seconds": %d,
                  "samples": %d,
                  "mspt_mean": %.4f,
                  "mspt_p50": %.4f,
                  "mspt_p95": %.4f,
                  "tps_1m_mean": %.4f,
                  "mspt_aggregation": %s,
                  "sample_cx_west": %d,
                  "sample_cx_east": %d,
                  "measurement_scope": %s,
                  "tick_model": %s,
                  "game_jvm_xms": %s,
                  "game_jvm_xmx": %s,
                  "game_jvm_max_mb": %d,
                  "chassis_present": %s,
                  "knob_entity_tick_budget": %d,
                  "knob_microtick_budget_ms": %d,
                  "knob_hopper_tick_budget": %d,
                  "knob_async_chunk_save": %s,
                  "knob_subregion_partition": %s,
                  "knob_budget_mspt_threshold": %.1f,
                  "knob_aligned_microticks": %s,
                  "knob_micro_phases": %d,
                  "knob_tick_wave_max_wait_ms": %d,
                  "knob_physics_substeps": %s,
                  "knob_physics_substep_count": %d,
                  "knob_subregion_mspt_threshold": %d,
                  "knob_subregion_mspt_clear": %d,
                  "expected_tnt": %d,
                  "tnt_start": %d,
                  "tnt_end": %d,
                  "fuse_mean_start": %.2f,
                  "fuse_mean_end": %.2f,
                  "fuse_drop": %.2f,
                  "fuse_drop_expected": %.2f,
                  "fuse_ticking_ok": %s,
                  "hoppers_start": %d,
                  "hoppers_end": %d,
                  "entities_start": %d,
                  "entities_end": %d,
                  "players_start": %d,
                  "players_end": %d,
                  "players_target": %d,
                  "players_ok": %s,
                  "bot_load": %s,
                  "villagers_start": %d,
                  "chunks_loaded_start": %d,
                  "chunks_loaded_end": %d,
                  "entity_top_start": %s,
                  "entity_top_end": %s,
                  "save_all_at": %s,
                  "save_fired_at_index": %s,
                  "mspt_pre_save_mean": %s,
                  "mspt_save_spike": %s,
                  "timestamp": %s,
                  "java": %s
                }
                """.formatted(
                quote(label),
                quote(scenario),
                warmup,
                sampleSec,
                mspt.size(),
                mean,
                p50,
                p95,
                tpsMean,
                quote(msptAggregation),
                sampleCxWest,
                sampleCxEast,
                quote(measurementScope),
                quote(tickModel),
                quote(gameXms),
                quote(gameXmx),
                gameJvmMaxMb,
                chassisPresent,
                knobs.entityTickBudget,
                knobs.microtickBudgetMs,
                knobs.hopperTickBudget,
                knobs.asyncChunkSave,
                knobs.subregionPartition,
                knobs.budgetMsptThreshold,
                knobs.alignedMicroticks,
                knobs.microPhases,
                knobs.tickWaveMaxWaitMs,
                knobs.physicsSubsteps,
                knobs.physicsSubstepCount,
                knobs.subregionMsptThreshold,
                knobs.subregionMsptClear,
                expectedTnt,
                start.tntAlive(),
                end.tntAlive(),
                start.fuseMean(),
                end.fuseMean(),
                fuseDrop,
                expectedFuseDrop,
                fuseOk,
                start.hoppers(),
                end.hoppers(),
                start.entitiesTotal(),
                end.entitiesTotal(),
                start.players(),
                end.players(),
                targetPlayers,
                playersOk,
                quote(botLoad),
                start.villagers(),
                start.loadedChunks(),
                end.loadedChunks(),
                quote(start.entityTop()),
                quote(end.entityTop()),
                saveAllAt >= 0 ? Integer.toString(saveAllAt) : "null",
                saveFiredAt >= 0 ? Integer.toString(saveFiredAt) : "null",
                Double.isNaN(msptPreSave) ? "null" : String.format(Locale.ROOT, "%.4f", msptPreSave),
                Double.isNaN(msptSaveSpike) ? "null" : String.format(Locale.ROOT, "%.4f", msptSaveSpike),
                quote(Instant.now().toString()),
                quote(System.getProperty("java.version", "?"))
        );
        try {
            Path p = Path.of(outPath);
            if (!p.isAbsolute()) {
                String home = System.getProperty("yapcore.home");
                if (home != null && !home.isBlank()) {
                    p = Path.of(home).resolve(outPath);
                }
            }
            Files.createDirectories(p.getParent());
            Files.writeString(p, json, StandardCharsets.UTF_8);
            plugin.getLogger().info("Wrote " + p.toAbsolutePath()
                    + " mspt_mean=" + String.format(Locale.ROOT, "%.3f", mean)
                    + " players=" + start.players()
                    + " tps=" + String.format(Locale.ROOT, "%.2f", tpsMean));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write results: " + e.getMessage());
        }
    }

    private static double mean(List<Double> v) {
        if (v.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (double d : v) {
            s += d;
        }
        return s / v.size();
    }

    private static double percentile(List<Double> v, double p) {
        if (v.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(v);
        sorted.sort(Double::compareTo);
        int i = Math.min(sorted.size() - 1, Math.max(0, (int) Math.round(p * (sorted.size() - 1))));
        return sorted.get(i);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
