package com.yapcore.bench;

/**
 * Cite-disclosed Folia ship knobs read from {@code -Dyap.bench.knob_*} /
 * {@code -Dyap.folia.*} (keeps {@link BenchSampler} under the 500-line domain gate).
 */
final class BenchKnobSnapshot {

    final int entityTickBudget;
    final int microtickBudgetMs;
    final int hopperTickBudget;
    final boolean asyncChunkSave;
    final boolean subregionPartition;
    final double budgetMsptThreshold;
    final boolean alignedMicroticks;
    final int microPhases;
    final int tickWaveMaxWaitMs;
    final boolean physicsSubsteps;
    final int physicsSubstepCount;
    final int subregionMsptThreshold;
    final int subregionMsptClear;

    private BenchKnobSnapshot(
            int entityTickBudget,
            int microtickBudgetMs,
            int hopperTickBudget,
            boolean asyncChunkSave,
            boolean subregionPartition,
            double budgetMsptThreshold,
            boolean alignedMicroticks,
            int microPhases,
            int tickWaveMaxWaitMs,
            boolean physicsSubsteps,
            int physicsSubstepCount,
            int subregionMsptThreshold,
            int subregionMsptClear) {
        this.entityTickBudget = entityTickBudget;
        this.microtickBudgetMs = microtickBudgetMs;
        this.hopperTickBudget = hopperTickBudget;
        this.asyncChunkSave = asyncChunkSave;
        this.subregionPartition = subregionPartition;
        this.budgetMsptThreshold = budgetMsptThreshold;
        this.alignedMicroticks = alignedMicroticks;
        this.microPhases = microPhases;
        this.tickWaveMaxWaitMs = tickWaveMaxWaitMs;
        this.physicsSubsteps = physicsSubsteps;
        this.physicsSubstepCount = physicsSubstepCount;
        this.subregionMsptThreshold = subregionMsptThreshold;
        this.subregionMsptClear = subregionMsptClear;
    }

    static BenchKnobSnapshot capture() {
        return new BenchKnobSnapshot(
                Integer.getInteger("yap.bench.knob_entity_tick_budget",
                        Integer.getInteger("yap.folia.entity-tick-budget", 0)),
                Integer.getInteger("yap.bench.knob_microtick_budget_ms",
                        Integer.getInteger("yap.folia.microtick-budget-ms", 0)),
                Integer.getInteger("yap.bench.knob_hopper_tick_budget",
                        Integer.getInteger("yap.folia.hopper-tick-budget", 0)),
                Boolean.parseBoolean(System.getProperty("yap.bench.knob_async_chunk_save",
                        System.getProperty("yap.folia.async-chunk-save", "false"))),
                Boolean.parseBoolean(System.getProperty("yap.bench.knob_subregion_partition",
                        System.getProperty("yap.folia.subregion-partition", "false"))),
                Double.parseDouble(System.getProperty("yap.bench.knob_budget_mspt_threshold",
                        System.getProperty("yap.folia.budget-mspt-threshold", "12"))),
                Boolean.parseBoolean(System.getProperty("yap.bench.knob_aligned_microticks",
                        System.getProperty("yap.folia.aligned-microticks", "false"))),
                Integer.getInteger("yap.bench.knob_micro_phases",
                        Integer.getInteger("yap.folia.micro-phases", 4)),
                Integer.getInteger("yap.bench.knob_tick_wave_max_wait_ms",
                        Integer.getInteger("yap.folia.tick-wave-max-wait-ms", 2)),
                Boolean.parseBoolean(System.getProperty("yap.bench.knob_physics_substeps",
                        System.getProperty("yap.folia.physics-substeps", "false"))),
                Integer.getInteger("yap.bench.knob_physics_substep_count",
                        Integer.getInteger("yap.folia.physics-substep-count", 4)),
                Integer.getInteger("yap.bench.knob_subregion_mspt_threshold",
                        Integer.getInteger("yap.folia.subregion-mspt-threshold", 20)),
                Integer.getInteger("yap.bench.knob_subregion_mspt_clear",
                        Integer.getInteger("yap.folia.subregion-mspt-clear", 16)));
    }
}
