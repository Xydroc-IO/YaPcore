package com.yapcore.fleet.local;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.model.FleetInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Builds the Folia JVM command line for a fleet instance (mirrors FoliaKernel flags). */
public final class InstanceJvmCommand {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Jvm");

    private InstanceJvmCommand() {
    }

    public static List<String> build(Path rootDir, ServerConfig config, Path jar) {
        return build(rootDir, config, jar, null);
    }

    public static List<String> build(
            Path rootDir, ServerConfig config, Path jar, FleetInstance instance) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        boolean bench = System.getProperty("yap.bench.scenario") != null
                && !System.getProperty("yap.bench.scenario").isBlank();
        if (bench) {
            cmd.add("-Xms" + System.getProperty("yap.bench.game_xms", "2G"));
            cmd.add("-Xmx" + System.getProperty("yap.bench.game_xmx", "4G"));
        } else {
            int[] heap = resolveHeap(config, instance);
            cmd.add("-Xms" + heap[0] + "M");
            cmd.add("-Xmx" + heap[1] + "M");
            cmd.add("-Djava.awt.headless=true");
            cmd.add("--enable-native-access=ALL-UNNAMED");
        }
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("yap.bench.")
                    || name.startsWith("yap.folia.")
                    || "yapcore.home".equals(name)) {
                String value = System.getProperty(name);
                if (value != null && !value.isBlank()) {
                    cmd.add("-D" + name + "=" + value);
                }
            }
        }
        appendSchedCompatAgent(rootDir, config, cmd);
        appendFoliaKnobs(config, cmd);
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());
        cmd.add("--nogui");
        return cmd;
    }

    /**
     * @return {@code [xmsMb, xmxMb]}
     */
    static int[] resolveHeap(ServerConfig config, FleetInstance instance) {
        int chassisDefault = Math.max(512, config.getRamMb() / 2);
        int xmx = instance != null && instance.ramMb() > 0 ? instance.ramMb() : chassisDefault;
        xmx = Math.max(256, xmx);
        int xms;
        if (instance != null && instance.ramMinMb() > 0) {
            xms = Math.min(instance.ramMinMb(), xmx);
        } else {
            xms = Math.min(512, xmx);
        }
        xms = Math.max(128, xms);
        return new int[] {xms, xmx};
    }

    private static void appendFoliaKnobs(ServerConfig config, List<String> cmd) {
        if (config.isFoliaTeleportTransactions()) {
            String jarSource = config.getFoliaJarSource() == null
                    ? "build"
                    : config.getFoliaJarSource().trim().toLowerCase(Locale.ROOT);
            if ("fetch".equals(jarSource) || "stock".equals(jarSource)) {
                LOG.severe("folia-teleport-transactions=true requires YaP-Folia (folia-jar-source=build).");
            }
            cmd.add("-Dyap.folia.teleport-transactions=true");
        }
        if (config.isFoliaAsyncChunkSave()) {
            cmd.add("-Dyap.folia.async-chunk-save=true");
        }
        int entityTickBudget = config.getFoliaEntityTickBudget();
        if (entityTickBudget > 0) {
            cmd.add("-Dyap.folia.entity-tick-budget=" + entityTickBudget);
        }
        cmd.add("-Dyap.folia.budget-mspt-threshold=" + config.getFoliaBudgetMsptThreshold());
        int maxDeferred = config.getFoliaEntityTickMaxDeferred();
        if (maxDeferred != 40) {
            cmd.add("-Dyap.folia.entity-tick-max-deferred=" + maxDeferred);
        }
        int hopperTickBudget = config.getFoliaHopperTickBudget();
        if (hopperTickBudget > 0) {
            cmd.add("-Dyap.folia.hopper-tick-budget=" + hopperTickBudget);
        }
        if (config.isFoliaScoreboardSwmr()) {
            cmd.add("-Dyap.folia.scoreboard-swmr=true");
        }
        int microtickMs = config.getFoliaMicrotickBudgetMs();
        if (microtickMs > 0) {
            cmd.add("-Dyap.folia.microtick-budget-ms=" + microtickMs);
        }
        if (config.isFoliaAlignedMicroticks()) {
            cmd.add("-Dyap.folia.aligned-microticks=true");
            cmd.add("-Dyap.folia.micro-phases=" + config.getFoliaMicroPhases());
            cmd.add("-Dyap.folia.tick-wave-max-wait-ms=" + config.getFoliaTickWaveMaxWaitMs());
        } else {
            cmd.add("-Dyap.folia.aligned-microticks=false");
        }
        if (config.isFoliaPhysicsSubsteps()) {
            cmd.add("-Dyap.folia.physics-substeps=true");
            cmd.add("-Dyap.folia.physics-substep-count=" + config.getFoliaPhysicsSubstepCount());
            cmd.add("-Dyap.folia.physics-substep-min-move=" + config.getFoliaPhysicsSubstepMinMove());
        } else {
            cmd.add("-Dyap.folia.physics-substeps=false");
        }
        long stealMs = config.getFoliaStealThresholdMs();
        if (stealMs != 3L) {
            cmd.add("-Dyap.folia.steal-threshold-ms=" + stealMs);
        }
        long sliceMs = config.getFoliaTaskSliceMs();
        if (sliceMs != 2L) {
            cmd.add("-Dyap.folia.task-slice-ms=" + sliceMs);
        }
        String gridExp = config.getFoliaGridExponent();
        if (gridExp != null && !gridExp.isBlank()) {
            cmd.add("-Dyap.folia.grid-exponent=" + gridExp.trim());
        }
        if (!config.isFoliaRegionMetrics()) {
            cmd.add("-Dyap.folia.region-metrics=false");
        }
        appendSubregion(config, cmd);
    }

    private static void appendSubregion(ServerConfig config, List<String> cmd) {
        if (!config.isFoliaSubregionPartition()) {
            cmd.add("-Dyap.folia.subregion-partition=false");
            return;
        }
        cmd.add("-Dyap.folia.subregion-partition=true");
        int shards = config.getFoliaSubregionShards();
        if (shards != 2) {
            cmd.add("-Dyap.folia.subregion-shards=" + shards);
        }
        int mspt = config.getFoliaSubregionMsptThreshold();
        if (mspt != 20) {
            cmd.add("-Dyap.folia.subregion-mspt-threshold=" + mspt);
        }
        int msptClear = config.getFoliaSubregionMsptClear();
        if (msptClear != 16) {
            cmd.add("-Dyap.folia.subregion-mspt-clear=" + msptClear);
        }
        int minSec = config.getFoliaSubregionMinSections();
        if (minSec != 4) {
            cmd.add("-Dyap.folia.subregion-min-sections=" + minSec);
        }
        int minEnt = config.getFoliaSubregionMinEntities();
        if (minEnt != 32) {
            cmd.add("-Dyap.folia.subregion-min-entities=" + minEnt);
        }
        int coalMspt = config.getFoliaSubregionCoalesceMspt();
        if (coalMspt != 8) {
            cmd.add("-Dyap.folia.subregion-coalesce-mspt=" + coalMspt);
        }
        int coalTicks = config.getFoliaSubregionCoalesceTicks();
        if (coalTicks != 100) {
            cmd.add("-Dyap.folia.subregion-coalesce-ticks=" + coalTicks);
        }
        int quietTicks = config.getFoliaSubregionCoalesceQuietTicks();
        if (quietTicks != 200) {
            cmd.add("-Dyap.folia.subregion-coalesce-quiet-ticks=" + quietTicks);
        }
        long coalWall = config.getFoliaSubregionCoalesceMinWallMs();
        if (coalWall != 30_000L) {
            cmd.add("-Dyap.folia.subregion-coalesce-min-wall-ms=" + coalWall);
        }
        cmd.add("-Dyap.folia.subregion-carve=" + config.isFoliaSubregionCarve());
        int partDelay = config.getFoliaSubregionPartitionDelayTicks();
        if (partDelay != 600) {
            cmd.add("-Dyap.folia.subregion-partition-delay-ticks=" + partDelay);
        }
        int gapInterval = config.getFoliaSubregionGapMaintainInterval();
        if (gapInterval != 10) {
            cmd.add("-Dyap.folia.subregion-gap-maintain-interval=" + gapInterval);
        }
    }

    private static void appendSchedCompatAgent(Path rootDir, ServerConfig config, List<String> cmd) {
        if (!config.isFoliaSchedCompat()) {
            return;
        }
        Path agent = resolveSchedAgentJar(rootDir);
        if (agent == null) {
            LOG.warning("folia-sched-compat=true but yap-sched-agent.jar not found");
            return;
        }
        cmd.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        cmd.add("-javaagent:" + agent.toAbsolutePath()
                + "=warn=" + config.isFoliaSchedCompatWarn() + ",metrics=true");
    }

    private static Path resolveSchedAgentJar(Path rootDir) {
        Path[] candidates = {
                rootDir.resolve("server/lib/yap-sched-agent.jar"),
                rootDir.resolve("lib/yap-sched-agent.jar"),
                rootDir.resolve("yap-first-party/engine/yap-sched-agent/build/libs/yap-sched-agent.jar")
        };
        for (Path pth : candidates) {
            if (Files.isRegularFile(pth)) {
                return pth.toAbsolutePath().normalize();
            }
        }
        return null;
    }
}
