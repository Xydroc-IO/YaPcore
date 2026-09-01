package com.yapcore.folia;

import com.yapcore.config.ServerConfig;
import com.yapcore.folia.process.FoliaProcess;
import com.yapcore.folia.surface.FoliaSurface;
import com.yapcore.paper.PaperOps;
import com.yapcore.paper.PaperPluginsLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Folia game authority — orchestrates managed Folia process (no Phase 3 spatial tick).
 */
public final class FoliaKernel {

    private static final Logger LOG = Logger.getLogger("YaPcore.Folia");

    private final Path rootDir;
    private final ServerConfig config;
    private FoliaProcess process;

    public FoliaKernel(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir;
        this.config = config;
    }

    public Path foliaDir() {
        return rootDir.resolve(config.getFoliaDir()).toAbsolutePath().normalize();
    }

    public int listenPort() {
        return config.foliaListenPort();
    }

    public boolean isEmbedded() {
        return config.isFoliaEmbed();
    }

    public boolean isRunning() {
        return process != null && process.isRunning();
    }

    public synchronized void start() throws IOException, InterruptedException {
        if (!config.isFoliaAuthority()) {
            LOG.info("Folia authority off (game-authority≠folia)");
            return;
        }
        if (Runtime.version().feature() < 25) {
            throw new IOException("Folia 26.2 requires Java 25+ (running " + Runtime.version() + ")");
        }
        if (config.isFoliaEmbed()) {
            startManagedPublic();
        } else {
            startWrapProxy();
        }
    }

    public synchronized void stop() {
        if (process != null) {
            process.stop();
            process = null;
        }
    }

    public String dispatchConsoleCommand(String line) {
        if (process == null) {
            return "Folia is not running";
        }
        return process.dispatchConsoleCommand(line);
    }

    private void startManagedPublic() throws IOException, InterruptedException {
        startProcess(listenPort(), bindIpForPublic(),
                "YaPcore managed Folia — owns public JE :" + listenPort());
        LOG.info("Managed Folia online on :" + listenPort());
    }

    private void startWrapProxy() throws IOException, InterruptedException {
        startProcess(config.getFoliaPort(), "127.0.0.1",
                "YaPcore Folia wrap — loopback proxy :" + config.getPort());
        LOG.info("Folia wrap online on 127.0.0.1:" + config.getFoliaPort());
    }

    private String bindIpForPublic() {
        String bind = config.getBindHost();
        if (bind == null || bind.isBlank() || "0.0.0.0".equals(bind)) {
            return "";
        }
        return bind;
    }

    private void startProcess(int port, String bindIp, String propsComment)
            throws IOException, InterruptedException {
        Path dir = foliaDir();
        Files.createDirectories(dir);
        PaperPluginsLayout.ensureUnified(rootDir, dir);
        Path jar = FoliaFiles.ensureFoliaJar(rootDir, dir, config);
        FoliaFiles.writeEula(dir);
        FoliaFiles.writeServerProperties(rootDir, dir, config, port, bindIp, propsComment);
        FoliaFiles.applyVelocitySupport(rootDir, dir, config);
        FoliaSurface.ensureMarker(dir);
        PaperOps.ensure(dir, config);
        List<String> cmd = buildCommand(jar);
        LOG.info("Starting Folia " + config.getFoliaVersion() + " port=" + port + " dir=" + dir);
        process = new FoliaProcess(dir);
        process.start(cmd, port, config.getFoliaReadyTimeoutSec());
    }

    private List<String> buildCommand(Path jar) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        boolean bench = System.getProperty("yap.bench.scenario") != null
                && !System.getProperty("yap.bench.scenario").isBlank();
        if (bench) {
            // Match scripts/bench/run-vs-folia.sh plain game JVM — no product-only flags.
            String xms = System.getProperty("yap.bench.game_xms", "2G");
            String xmx = System.getProperty("yap.bench.game_xmx", "4G");
            cmd.add("-Xms" + xms);
            cmd.add("-Xmx" + xmx);
        } else {
            int ram = Math.max(512, config.getRamMb() / 2);
            cmd.add("-Xms" + Math.min(512, ram) + "M");
            cmd.add("-Xmx" + ram + "M");
            cmd.add("-Djava.awt.headless=true");
            cmd.add("--enable-native-access=ALL-UNNAMED");
        }
        // Forward MSPT bench / home / YaP Folia knobs into the managed Folia JVM.
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
        appendSchedCompatAgent(cmd);
        if (config.isFoliaTeleportTransactions()) {
            String jarSource = config.getFoliaJarSource() == null
                    ? "build"
                    : config.getFoliaJarSource().trim().toLowerCase(Locale.ROOT);
            if ("fetch".equals(jarSource) || "stock".equals(jarSource)) {
                LOG.severe("folia-teleport-transactions=true requires YaP-Folia (folia-jar-source=build). "
                        + "Stock Fill Folia has no YapTeleportTransaction — run ./scripts/build-yap-folia.sh "
                        + "or set folia-teleport-transactions=false.");
            }
            cmd.add("-Dyap.folia.teleport-transactions=true");
        }
        // Product path: server.properties → Folia JVM (smoke may also set -Dyap.folia.* on chassis).
        if (config.isFoliaAsyncChunkSave()) {
            cmd.add("-Dyap.folia.async-chunk-save=true");
        }
        int entityTickBudget = config.getFoliaEntityTickBudget();
        if (entityTickBudget > 0) {
            cmd.add("-Dyap.folia.entity-tick-budget=" + entityTickBudget);
        }
        if (config.isFoliaScoreboardSwmr()) {
            cmd.add("-Dyap.folia.scoreboard-swmr=true");
        }
        int microtickMs = config.getFoliaMicrotickBudgetMs();
        if (microtickMs > 0) {
            cmd.add("-Dyap.folia.microtick-budget-ms=" + microtickMs);
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
        if (config.isFoliaSubregionPartition()) {
            cmd.add("-Dyap.folia.subregion-partition=true");
            int shards = config.getFoliaSubregionShards();
            if (shards != 2) {
                cmd.add("-Dyap.folia.subregion-shards=" + shards);
            }
            int mspt = config.getFoliaSubregionMsptThreshold();
            if (mspt != 20) {
                cmd.add("-Dyap.folia.subregion-mspt-threshold=" + mspt);
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
            if (!config.isFoliaSubregionCarve()) {
                cmd.add("-Dyap.folia.subregion-carve=false");
            }
            int partDelay = config.getFoliaSubregionPartitionDelayTicks();
            if (partDelay != 600) {
                cmd.add("-Dyap.folia.subregion-partition-delay-ticks=" + partDelay);
            }
            int gapInterval = config.getFoliaSubregionGapMaintainInterval();
            if (gapInterval != 10) {
                cmd.add("-Dyap.folia.subregion-gap-maintain-interval=" + gapInterval);
            }
        }
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());
        cmd.add("--nogui");
        return cmd;
    }

    private void appendSchedCompatAgent(List<String> cmd) {
        if (!config.isFoliaSchedCompat()) {
            return;
        }
        Path agent = resolveSchedAgentJar();
        if (agent == null || !Files.isRegularFile(agent)) {
            LOG.warning("folia-sched-compat=true but yap-sched-agent.jar not found under server/lib or lib/");
            return;
        }
        cmd.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        StringBuilder arg = new StringBuilder("-javaagent:");
        arg.append(agent.toAbsolutePath());
        arg.append('=');
        arg.append("warn=").append(config.isFoliaSchedCompatWarn());
        arg.append(",metrics=true");
        cmd.add(arg.toString());
        LOG.info("Folia sched-compat agent: " + agent.getFileName());
    }

    private Path resolveSchedAgentJar() {
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
