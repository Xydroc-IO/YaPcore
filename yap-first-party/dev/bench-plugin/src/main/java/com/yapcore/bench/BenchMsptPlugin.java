package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * MSPT scoreboard harness.
 * Scenarios: idle | entity | farm | heavypop | spawncollapse | highpop | fullcite
 */
public final class BenchMsptPlugin extends JavaPlugin implements Listener {

    private String scenario = "idle";
    private BenchWorldPrep worldPrep;
    private BenchSampler sampler;

    @Override
    public void onEnable() {
        worldPrep = new BenchWorldPrep(this);
        sampler = new BenchSampler(this, worldPrep);
        scenario = System.getProperty("yap.bench.scenario", "idle").toLowerCase(Locale.ROOT);
        int seconds = Integer.getInteger("yap.bench.seconds", 30);
        int warmup = Integer.getInteger("yap.bench.warmup", 10);
        String label = System.getProperty("yap.bench.label", "run");
        String out = System.getProperty("yap.bench.out", "bench/results/last.json");

        getLogger().info("MSPT bench scenario=" + scenario + " warmup=" + warmup
                + "s sample=" + seconds + "s label=" + label);

        if ("highpop".equals(scenario) || "fullcite".equals(scenario)) {
            Bukkit.getPluginManager().registerEvents(this, this);
        }

        YapSched.globalLater(this, () -> {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (world == null) {
                getLogger().severe("No world — aborting bench");
                Bukkit.shutdown();
                return;
            }
            if ("highpop".equals(scenario) || "fullcite".equals(scenario)) {
                if (YapSched.isRegionized()) {
                    BenchRegionLoad.preparePopBench(this, world, scenario, expectedTnt ->
                            startPopBench(world, scenario, label, out, warmup, seconds, expectedTnt));
                } else {
                    int expectedTnt = "fullcite".equals(scenario)
                            ? worldPrep.prepareFullcite(world)
                            : worldPrep.prepareHighpop(world);
                    startPopBench(world, scenario, label, out, warmup, seconds, expectedTnt);
                }
            } else if (YapSched.isRegionized()) {
                BenchRegionLoad.prepare(this, world, scenario, expectedTnt ->
                        YapSched.globalLater(this, () ->
                                        sampler.sampleAndWrite(world, scenario, label, out, warmup, seconds, expectedTnt),
                                warmup * 20L));
            } else {
                int expectedTnt = worldPrep.prepare(world, scenario);
                YapSched.globalLater(this, () ->
                                sampler.sampleAndWrite(world, scenario, label, out, warmup, seconds, expectedTnt),
                        warmup * 20L);
            }
        }, 40L);
    }

    @EventHandler
    public void onBotJoin(PlayerJoinEvent event) {
        if (!"highpop".equals(scenario) && !"fullcite".equals(scenario)) {
            return;
        }
        Player p = event.getPlayer();
        String name = p.getName();
        if (!name.startsWith("yapbot_")) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(name.substring("yapbot_".length()));
        } catch (NumberFormatException e) {
            return;
        }
        int[] xz = BenchSpreadGrid.homeForBotId(id);
        World world = p.getWorld();
        int y = world.getHighestBlockYAt(xz[0], xz[1]) + 1;
        Location dest = new Location(world, xz[0] + 0.5, y, xz[1] + 0.5);
        YapSched.entity(this, p, () -> {
            if (p.isOnline()) {
                p.teleport(dest);
            }
        });
    }

    private void startPopBench(World world, String scenario, String label, String out,
                               int warmup, int sampleSec, int expectedTnt) {
        writeReadyMarker(world);
        if ("fullcite".equals(scenario)) {
            getLogger().info("fullcite ready — bots + expectedTNT=" + expectedTnt
                    + " (region-safe prep)");
        } else {
            getLogger().info("highpop world fixtures ready — waiting for bots "
                    + "(target players=" + Integer.getInteger("yap.bench.players", 100) + ")");
        }
        waitForBotsThenSample(world, scenario, label, out, warmup, sampleSec, expectedTnt);
    }

    private void writeReadyMarker(World world) {
        try {
        String rootProp = System.getProperty("yap.bench.root",
                System.getProperty("yapcore.home", "."));
        Path home = Path.of(rootProp).toAbsolutePath().normalize();
            Path marker = home.resolve("bench").resolve("highpop-ready.port");
            Files.createDirectories(marker.getParent());
            // Public bot connect port (Via front) may differ from Bukkit.getPort() (Paper loopback).
            int paperPort = Bukkit.getPort();
            int botPort = Integer.getInteger("yap.bench.bot_port", paperPort);
            String body = botPort + "\n" + world.getName() + "\n" + Instant.now()
                    + "\npaper_port=" + paperPort + "\n";
            Files.writeString(marker, body, StandardCharsets.UTF_8);
            Files.writeString(Path.of("yap-bench-ready.port"), body, StandardCharsets.UTF_8);
            getLogger().info("ready marker → " + marker.toAbsolutePath()
                    + " bot_port=" + botPort + " paper_port=" + paperPort);
        } catch (IOException e) {
            getLogger().severe("ready marker write failed: " + e.getMessage());
        }
    }

    private void waitForBotsThenSample(World world, String scenario, String label, String out,
                                       int warmupSec, int sampleSec, int expectedTnt) {
        int target = Integer.getInteger("yap.bench.players", 100);
        int joinTimeout = Integer.getInteger("yap.bench.join_timeout", 180);
        // Match players_ok: start sample once ≥90% are online (or timeout).
        int need = Math.max(1, (int) Math.ceil(target * 0.90));
        // Require a stable hold before warmup — avoids sampling while still joining/dropping.
        int stableNeed = Integer.getInteger("yap.bench.join_stable_sec", 15);
        final int[] waited = {0};
        final int[] stable = {0};
        final YapTask[] joinGate = new YapTask[1];
        joinGate[0] = YapSched.globalTimer(this, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            waited[0]++;
            if (online >= need) {
                stable[0]++;
            } else {
                stable[0] = 0;
            }
            boolean ready = stable[0] >= stableNeed;
            boolean timedOut = waited[0] >= joinTimeout;
            if (ready || timedOut) {
                joinGate[0].cancel();
                getLogger().info(scenario + " join gate — online=" + online + "/" + target
                        + " need≥" + need + " stable=" + stable[0] + "s/" + stableNeed
                        + "s waited=" + waited[0] + "s"
                        + (timedOut && !ready ? " (TIMEOUT)" : "")
                        + " — starting warmup " + warmupSec + "s");
                // After warmup, require population still held (physics enable often
                // drops bots; sampling at the trough gamed MSPT / failed fairness).
                YapSched.globalLater(this, () ->
                                waitPostWarmupThenSample(world, scenario, label, out,
                                        warmupSec, sampleSec, expectedTnt, need, target),
                        warmupSec * 20L);
            } else if (waited[0] % 10 == 0) {
                getLogger().info("waiting for bots… online=" + online + "/" + target
                        + " (need≥" + need + ", stable " + stable[0] + "/" + stableNeed + "s)");
            }
        }, 20L, 20L);
    }

    private void waitPostWarmupThenSample(World world, String scenario, String label, String out,
                                          int warmupSec, int sampleSec, int expectedTnt,
                                          int need, int target) {
        int settleTimeout = Integer.getInteger("yap.bench.post_warmup_sec", 90);
        final int[] waited = {0};
        final int[] stable = {0};
        int stableNeed = Integer.getInteger("yap.bench.post_warmup_stable_sec", 10);
        final YapTask[] settleGate = new YapTask[1];
        settleGate[0] = YapSched.globalTimer(this, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            waited[0]++;
            if (online >= need) {
                stable[0]++;
            } else {
                stable[0] = 0;
            }
            if (stable[0] >= stableNeed || waited[0] >= settleTimeout) {
                settleGate[0].cancel();
                getLogger().info(scenario + " post-warmup gate — online=" + online + "/" + target
                        + " need≥" + need + " stable=" + stable[0] + "s/" + stableNeed
                        + "s waited=" + waited[0] + "s — starting sample " + sampleSec + "s");
                // Same entity budget across forks: no wild mobs / item-XP drift from bot combat.
                if (YapSched.isRegionized()) {
                    YapSched.global(this, () -> world.setGameRule(GameRule.SPAWN_MONSTERS, false));
                } else {
                    worldPrep.enforceEntityParity(world, false);
                }
                sampler.sampleAndWrite(world, scenario, label, out, warmupSec, sampleSec, expectedTnt);
            } else if (waited[0] % 10 == 0) {
                getLogger().info("post-warmup settle… online=" + online + "/" + target
                        + " (need≥" + need + ", stable " + stable[0] + "/" + stableNeed + "s)");
            }
        }, 20L, 20L);
    }
}
