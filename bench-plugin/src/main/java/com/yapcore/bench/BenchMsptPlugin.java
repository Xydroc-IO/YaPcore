package com.yapcore.bench;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MSPT scoreboard harness — scenarios: idle | entity | farm.
 * JVM props: yap.bench.scenario, yap.bench.seconds, yap.bench.warmup,
 * yap.bench.label, yap.bench.out (results JSON path).
 */
public final class BenchMsptPlugin extends JavaPlugin {

    /** Interior chunks (not on quadrant borders). */
    private static final int[][] INTERIOR = {
            {8, 8}, {-9, 8}, {8, -9}, {-9, -9}
    };

    @Override
    public void onEnable() {
        String scenario = System.getProperty("yap.bench.scenario", "idle").toLowerCase(Locale.ROOT);
        int seconds = Integer.getInteger("yap.bench.seconds", 30);
        int warmup = Integer.getInteger("yap.bench.warmup", 10);
        String label = System.getProperty("yap.bench.label", "run");
        String out = System.getProperty("yap.bench.out", "bench/results/last.json");

        getLogger().info("MSPT bench scenario=" + scenario + " warmup=" + warmup
                + "s sample=" + seconds + "s label=" + label);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (world == null) {
                getLogger().severe("No world — aborting bench");
                Bukkit.shutdown();
                return;
            }
            prepare(world, scenario);
            Bukkit.getScheduler().runTaskLater(this, () ->
                    sampleAndWrite(scenario, label, out, warmup, seconds), warmup * 20L);
        }, 40L);
    }

    private void prepare(World world, String scenario) {
        // Avoid stacking load across repeated bench runs
        for (int[] c : INTERIOR) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.getChunkAt(c[0], c[1]).getEntities();
        }
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (!(e instanceof org.bukkit.entity.Player)) {
                e.remove();
            }
        }
        switch (scenario) {
            case "entity" -> {
                // Primed TNT always fully ticks (EAR cannot idle it) — fair MSPT load.
                int perQuad = Integer.getInteger("yap.bench.entities", 120);
                for (int[] c : INTERIOR) {
                    int bx = (c[0] << 4) + 8;
                    int bz = (c[1] << 4) + 8;
                    world.getChunkAt(c[0], c[1]).load(true);
                    int y = Math.max(world.getHighestBlockYAt(bx, bz) + 2, 80);
                    for (int i = 0; i < perQuad; i++) {
                        org.bukkit.entity.TNTPrimed tnt = world.spawn(
                                new Location(world, bx + (i % 8) * 0.1, y, bz + (i / 8) * 0.1),
                                org.bukkit.entity.TNTPrimed.class);
                        tnt.setFuseTicks(20 * 60 * 10); // 10 minutes
                        tnt.setYield(0f);
                        tnt.setIsIncendiary(false);
                    }
                }
                getLogger().info("Spawned primed TNT in 4 interior quads x" + perQuad);
            }
            case "farm" -> {
                for (int[] c : INTERIOR) {
                    world.getChunkAt(c[0], c[1]).load(true);
                    int bx = c[0] << 4;
                    int bz = c[1] << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int y = world.getHighestBlockYAt(bx + x, bz + z);
                            world.getBlockAt(bx + x, y, bz + z).setType(Material.FARMLAND);
                            world.getBlockAt(bx + x, y + 1, bz + z).setType(Material.WHEAT);
                        }
                    }
                }
                getLogger().info("Planted wheat farms in 4 interior quads");
            }
            default -> getLogger().info("Idle scenario — no load injected");
        }
    }

    private void sampleAndWrite(String scenario, String label, String out,
                                int warmupSec, int sampleSec) {
        List<Double> mspt = new ArrayList<>();
        List<Double> tps1m = new ArrayList<>();
        final int[] left = {sampleSec};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            double avg = Bukkit.getServer().getAverageTickTime();
            double[] tps = Bukkit.getServer().getTPS();
            mspt.add(avg);
            tps1m.add(tps.length > 0 ? tps[0] : 0);
            left[0]--;
            if (left[0] <= 0) {
                task.cancel();
                writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m);
                getLogger().info("Bench complete — shutting down");
                Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
            }
        }, 20L, 20L);
    }

    private void writeJson(String scenario, String label, String outPath,
                           int warmup, int sampleSec, List<Double> mspt, List<Double> tps) {
        double mean = mean(mspt);
        double p50 = percentile(mspt, 0.50);
        double p95 = percentile(mspt, 0.95);
        double tpsMean = mean(tps);
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
            getLogger().info("Wrote " + p.toAbsolutePath()
                    + " mspt_mean=" + String.format(Locale.ROOT, "%.3f", mean)
                    + " tps=" + String.format(Locale.ROOT, "%.2f", tpsMean));
        } catch (IOException e) {
            getLogger().severe("Failed to write results: " + e.getMessage());
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
