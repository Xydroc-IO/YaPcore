package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BenchSampler {
    private final JavaPlugin plugin;
    private final BenchWorldPrep worldPrep;

    BenchSampler(JavaPlugin plugin, BenchWorldPrep worldPrep) {
        this.plugin = plugin;
        this.worldPrep = worldPrep;
    }

    record LoadSnapshot(int tntAlive, double fuseMean, int hoppers, int entitiesTotal,
                                int players, int villagers, int loadedChunks,
                                String entityTop) {
    }

    private LoadSnapshot snapshotLoad(World world) {
        int tnt = 0;
        long fuseSum = 0;
        int hoppers = 0;
        int entities = 0;
        int villagers = 0;
        java.util.Map<String, Integer> byType = new java.util.HashMap<>();
        for (Entity e : world.getEntities()) {
            entities++;
            String key = e.getType().name();
            byType.merge(key, 1, Integer::sum);
            if (e instanceof TNTPrimed tntPrimed) {
                tnt++;
                fuseSum += tntPrimed.getFuseTicks();
            }
            if (e instanceof Villager) {
                villagers++;
            }
        }
        String entityTop = byType.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(12)
                .map(en -> en.getKey() + "=" + en.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        for (int[][] piles : BenchWorldPrep.HEAVY_PILES) {
            int[] c = piles[0];
            var chunk = world.getChunkAt(c[0], c[1]);
            if (!chunk.isLoaded()) {
                continue;
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers++;
                }
            }
        }
        // highpop / near-spawn fixtures
        for (int[] c : BenchWorldPrep.INTERIOR) {
            var chunk = world.getChunkAt(c[0], c[1]);
            if (!chunk.isLoaded()) {
                continue;
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers++;
                }
            }
        }
        double fuseMean = tnt == 0 ? 0.0 : (double) fuseSum / tnt;
        return new LoadSnapshot(tnt, fuseMean, hoppers, entities,
                Bukkit.getOnlinePlayers().size(), villagers, world.getLoadedChunks().length,
                entityTop);
    }

    void sampleAndWrite(World world, String scenario, String label, String out,
                                int warmupSec, int sampleSec, int expectedTnt) {
        if (YapSched.isRegionized()) {
            BenchRegionLoad.snapshotAsync(plugin, world, start -> {
                plugin.getLogger().info("Load@sample-start players=" + start.players()
                        + " entities=" + start.entitiesTotal()
                        + " villagers=" + start.villagers()
                        + " hoppers=" + start.hoppers()
                        + " chunks=" + start.loadedChunks()
                        + " tnt=" + start.tntAlive()
                        + " types=" + start.entityTop());
                runSampler(world, scenario, label, out, warmupSec, sampleSec, expectedTnt,
                        toLegacySnapshot(start));
            });
            return;
        }
        LoadSnapshot start = snapshotLoad(world);
        plugin.getLogger().info("Load@sample-start players=" + start.players()
                + " entities=" + start.entitiesTotal()
                + " villagers=" + start.villagers()
                + " hoppers=" + start.hoppers()
                + " chunks=" + start.loadedChunks()
                + " tnt=" + start.tntAlive()
                + " types=" + start.entityTop());
        runSampler(world, scenario, label, out, warmupSec, sampleSec, expectedTnt, start);
    }

    static LoadSnapshot toLegacySnapshot(BenchRegionLoad.LoadSnapshot s) {
        return new LoadSnapshot(s.tntAlive(), s.fuseMean(), s.hoppers(), s.entitiesTotal(),
                s.players(), s.villagers(), s.loadedChunks(), s.entityTop());
    }

    void runSampler(World world, String scenario, String label, String out,
                            int warmupSec, int sampleSec, int expectedTnt, LoadSnapshot start) {
        List<Double> mspt = new ArrayList<>();
        List<Double> tps1m = new ArrayList<>();
        final int[] left = {sampleSec};
        final YapTask[] sampler = new YapTask[1];
        // Folia: sample on the spawn/hot-region thread — getAverageTickTime() is region-local.
        // spawncollapse / heavypop load lives around chunk (0,0); global-region MSPT is near-empty.
        final int sampleCx = 0;
        final int sampleCz = 0;
        Runnable tick = () -> {
            try {
                mspt.add(Bukkit.getServer().getAverageTickTime());
            } catch (UnsupportedOperationException uoe) {
                // Not on a region thread somehow — skip sample
                return;
            }
            double regionTps = Double.NaN;
            try {
                double[] rt = Bukkit.getServer().getTPS();
                if (YapSched.isRegionized()) {
                    try {
                        java.lang.reflect.Method m = Bukkit.getServer().getClass()
                                .getMethod("getRegionTPS", org.bukkit.World.class, int.class, int.class);
                        Object regObj = m.invoke(Bukkit.getServer(), world, sampleCx, sampleCz);
                        if (regObj instanceof double[] reg && reg.length > 0) {
                            regionTps = reg[0];
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Paper API without Folia region TPS
                    }
                }
                tps1m.add(!Double.isNaN(regionTps) ? regionTps : (rt.length > 0 ? rt[0] : 0));
            } catch (Throwable t) {
                double[] tps = Bukkit.getServer().getTPS();
                tps1m.add(tps.length > 0 ? tps[0] : 0);
            }
            left[0]--;
            if (left[0] <= 0) {
                sampler[0].cancel();
                if (YapSched.isRegionized()) {
                    BenchRegionLoad.snapshotAsync(plugin, world, endR -> {
                        LoadSnapshot end = toLegacySnapshot(endR);
                        plugin.getLogger().info("Load@sample-end players=" + end.players()
                                + " entities=" + end.entitiesTotal()
                                + " chunks=" + end.loadedChunks());
                        writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                                expectedTnt, start, end);
                        plugin.getLogger().info("Bench complete — shutting down");
                        YapSched.globalLater(plugin, Bukkit::shutdown, 20L);
                    });
                } else {
                    LoadSnapshot end = snapshotLoad(world);
                    plugin.getLogger().info("Load@sample-end players=" + end.players()
                            + " entities=" + end.entitiesTotal()
                            + " chunks=" + end.loadedChunks());
                    writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                            expectedTnt, start, end);
                    plugin.getLogger().info("Bench complete — shutting down");
                    YapSched.globalLater(plugin, Bukkit::shutdown, 20L);
                }
            }
        };
        if (YapSched.isRegionized()) {
            plugin.getLogger().info("MSPT sampler on region chunk (" + sampleCx + "," + sampleCz
                    + ") — Folia region-local getAverageTickTime()");
            sampler[0] = YapSched.regionChunkTimer(plugin, world, sampleCx, sampleCz, tick, 20L, 20L);
        } else {
            sampler[0] = YapSched.globalTimer(plugin, tick, 20L, 20L);
        }
    }

    void writeJson(String scenario, String label, String outPath,
                           int warmup, int sampleSec, List<Double> mspt, List<Double> tps,
                           int expectedTnt, LoadSnapshot start, LoadSnapshot end) {
        double mean = mean(mspt);
        double p50 = percentile(mspt, 0.50);
        double p95 = percentile(mspt, 0.95);
        double tpsMean = mean(tps);
        double fuseDrop = start.fuseMean() - end.fuseMean();
        double expectedFuseDrop = sampleSec * 20.0;
        boolean fuseOk = start.tntAlive() == 0
                || (fuseDrop >= expectedFuseDrop * 0.50 && end.tntAlive() >= start.tntAlive() * 0.98);
        int targetPlayers = Integer.getInteger("yap.bench.players", 0);
        // Highpop / fullcite: must HOLD population through the sample, not just
        // clear the join gate. Start+end ≥90% of target — bleeding 250→130 fails.
        int needHold = Math.max(1, (int) Math.ceil(targetPlayers * 0.90));
        boolean playersOk = (!"highpop".equals(scenario) && !"fullcite".equals(scenario))
                || (targetPlayers <= 0)
                || (start.players() >= needHold && end.players() >= needHold);
        // cite-stable = keepalive-only swarm (physics OFF). Not an MSPT gameplay cite.
        String botLoad = System.getProperty("yap.bench.bot_load", "active");
        if (botLoad == null || botLoad.isBlank()) {
            botLoad = "active";
        }
        String measurementScope = System.getProperty("yap.bench.measurement_scope", "game_tick_mspt");
        String gameXms = System.getProperty("yap.bench.game_xms", "");
        String gameXmx = System.getProperty("yap.bench.game_xmx", "");
        long gameJvmMaxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean chassisPresent = Boolean.parseBoolean(System.getProperty("yap.bench.chassis_present", "false"));
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
                  "measurement_scope": %s,
                  "game_jvm_xms": %s,
                  "game_jvm_xmx": %s,
                  "game_jvm_max_mb": %d,
                  "chassis_present": %s,
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
                quote(measurementScope),
                quote(gameXms),
                quote(gameXmx),
                gameJvmMaxMb,
                chassisPresent,
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

    static double mean(List<Double> v) {
        if (v.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (double d : v) {
            s += d;
        }
        return s / v.size();
    }

    static double percentile(List<Double> v, double p) {
        if (v.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(v);
        sorted.sort(Double::compareTo);
        int i = Math.min(sorted.size() - 1, Math.max(0, (int) Math.round(p * (sorted.size() - 1))));
        return sorted.get(i);
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
