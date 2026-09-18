package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lab harness: contiguous strip on ServerLoad → force-partition (0032) → off-owner BLOCKS pulse.
 * Lab knobs only; never a ship default.
 */
final class BenchPartitionCut {

    private BenchPartitionCut() {
    }

    static void run(JavaPlugin plugin, World world) {
        if (BenchRegionSpawnChunks.stripHalfWidth() <= 0) {
            System.setProperty("yap.bench.strip_half_width", "64");
        }
        System.setProperty("yap.bench.strip_two_phase", "true");
        System.setProperty("yap.bench.contiguous_carve", "false");
        if (Integer.getInteger("yap.bench.strip_gap_half", 0) <= 0) {
            System.setProperty("yap.bench.strip_gap_half", "32");
        }
        plugin.getLogger().info("partition-cut — lobe gap stripHalf="
                + BenchRegionSpawnChunks.stripHalfWidth()
                + " gapHalf=" + BenchRegionSpawnChunks.stripGapHalf());
        // Poll immediately. RegionScheduler.execute on 192 far chunks can hang across a
        // force-partition (destroyed region #0), which used to gate the probe forever.
        poll(plugin);
        pinAndInject(plugin, world, spawned -> {
            plugin.getLogger().info("partition-cut fixtures ready TNT=" + spawned
                    + " stripHalf=" + BenchRegionSpawnChunks.stripHalfWidth()
                    + " gapHalf=" + BenchRegionSpawnChunks.stripGapHalf());
        });
    }

    /** Per-chunk on the owning region thread. Server thread cannot load the strip. */
    private static void pinAndInject(JavaPlugin plugin, World world, java.util.function.IntConsumer onReady) {
        Set<long[]> chunks = BenchRegionSpawnChunks.spawnCollapseLobePinChunks();
        int west = -Math.max(24, BenchRegionSpawnChunks.stripHalfWidth() - 4);
        try {
            world.setSpawnLocation(west << 4, 80, 0);
        } catch (IllegalStateException ignored) {
        }
        try {
            world.setGameRule(GameRule.SPAWN_MONSTERS, false);
        } catch (IllegalStateException ignored) {
        }
        if (chunks.isEmpty()) {
            onReady.accept(0);
            return;
        }
        java.util.ArrayList<long[]> list = new java.util.ArrayList<>(chunks);
        int size = list.size();
        int totalTnt = Integer.getInteger("yap.bench.entities", 200);
        int totalMobs = Integer.getInteger("yap.bench.mobs", 64);
        AtomicInteger left = new AtomicInteger(size);
        AtomicInteger spawned = new AtomicInteger();
        EntityType[] types = {EntityType.ZOMBIE, EntityType.PIG, EntityType.COW};
        plugin.getLogger().info("partition-cut scheduling " + size + " strip chunks on region threads");
        for (int i = 0; i < size; i++) {
            long[] c = list.get(i);
            int cx = (int) c[0];
            int cz = (int) c[1];
            int tntHere = totalTnt / size + (i < totalTnt % size ? 1 : 0);
            int mobHere = totalMobs / size + (i < totalMobs % size ? 1 : 0);
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                try {
                    BenchRegionLoadLoops.pinChunk(plugin, world, cx, cz);
                    BenchRegionLoadLoops.tryForceLoad(world, cx, cz);
                    world.getChunkAt(cx, cz).load(true);
                    int bx = (cx << 4) + 8;
                    int bz = (cz << 4) + 8;
                    int y = Math.max(world.getHighestBlockYAt(bx, bz) + 2, 80);
                    for (int t = 0; t < tntHere; t++) {
                        TNTPrimed tnt = world.spawn(new Location(world, bx, y, bz), TNTPrimed.class);
                        tnt.setFuseTicks(20 * 60 * 10);
                        tnt.setYield(0f);
                        tnt.setIsIncendiary(false);
                        spawned.incrementAndGet();
                    }
                    for (int m = 0; m < mobHere; m++) {
                        var e = world.spawnEntity(
                                new Location(world, bx + 0.5, y, bz + 0.5), types[m % types.length]);
                        if (e instanceof LivingEntity living) {
                            living.setRemoveWhenFarAway(false);
                            living.setPersistent(true);
                        }
                    }
                } finally {
                    if (left.decrementAndGet() == 0) {
                        onReady.accept(spawned.get());
                    }
                }
            });
        }
    }

    private static void poll(JavaPlugin plugin) {
        Path probe = Path.of(System.getProperty("yap.folia.scheduler-probe-file", "yap-scheduler-probe.txt"));
        int waitSec = Integer.getInteger("yap.bench.partition_wait_sec", 180);
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger afterPulse = new AtomicInteger();
        AtomicBoolean pulsed = new AtomicBoolean();
        AtomicLong slippedBefore = new AtomicLong();
        YapTask[] handle = new YapTask[1];
        handle[0] = YapSched.asyncTimer(plugin, () -> {
            int t = ticks.incrementAndGet();
            long splits = probeLong(probe, "splits");
            long force = probeLong(probe, "force_partitions");
            if (!pulsed.get() && (splits >= 1 || force >= 1)) {
                // East lobe, just outside the empty gap — must be a different region than west.
                int cx = Math.max(2, BenchRegionSpawnChunks.stripGapHalf() + 8);
                try {
                    writePulseRequest(probe, cx);
                    slippedBefore.set(probeLong(probe, "slipped"));
                    pulsed.set(true);
                    plugin.getLogger().info("partition-cut pulse request chunk=" + cx
                            + ",0 splits=" + splits + " force=" + force);
                } catch (Exception e) {
                    finish(plugin, probe, false, "pulse-request-failed", t, slippedBefore.get());
                    cancel(handle);
                    return;
                }
            }
            if (pulsed.get()) {
                int ap = afterPulse.incrementAndGet();
                long ran = probeLong(probe, "pulses_ran");
                long queued = probeLong(probe, "pulses_queued");
                if (ran >= 1 || (queued >= 1 && ap >= 15) || ap >= 30) {
                    cancel(handle);
                    long timeouts = probeLong(probe, "wave_timeouts");
                    boolean ok = ran >= 1 && probeLong(probe, "splits") >= 1 && timeouts == 0;
                    String reason = ok ? "ok"
                            : (timeouts != 0 ? "wave-timeouts" : "pulse-did-not-run-in-BLOCKS");
                    finish(plugin, probe, ok, reason, t, slippedBefore.get());
                }
                return;
            }
            if (t >= waitSec) {
                cancel(handle);
                finish(plugin, probe, false, "no-force-partition", t, 0);
            } else if (t % 15 == 0) {
                plugin.getLogger().info("partition-cut waiting splits=" + splits
                        + " force=" + force + " sec=" + t + "/" + waitSec);
            }
        }, 20L, 20L);
    }

    private static void cancel(YapTask[] handle) {
        if (handle[0] != null) {
            handle[0].cancel();
        }
    }

    private static void writePulseRequest(Path probe, int cx) throws Exception {
        String body = cx + " 0\n";
        java.util.LinkedHashSet<Path> targets = new java.util.LinkedHashSet<>();
        targets.add(Path.of("yap-scheduler-pulse.request"));
        Path probeParent = probe.toAbsolutePath().getParent();
        if (probeParent != null) {
            targets.add(probeParent.resolve("yap-scheduler-pulse.request"));
        }
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            targets.add(Path.of(userDir, "yap-scheduler-pulse.request"));
        }
        for (Path p : targets) {
            Files.writeString(p, body, StandardCharsets.UTF_8);
        }
    }

    private static long probeLong(Path file, String key) {
        if (!Files.isRegularFile(file)) {
            return 0L;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String prefix = key + "=";
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    return Long.parseLong(line.substring(prefix.length()).trim());
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static void finish(JavaPlugin plugin, Path probe, boolean pass, String reason,
                               int ticks, long slippedBefore) {
        long splits = probeLong(probe, "splits");
        long force = probeLong(probe, "force_partitions");
        long queued = probeLong(probe, "pulses_queued");
        long ran = probeLong(probe, "pulses_ran");
        long slipped = probeLong(probe, "slipped");
        long drainedBlocks = probeLong(probe, "drained_blocks");
        long ranBlocks = probeLong(probe, "ran_blocks");
        long misses = probeLong(probe, "split_misses");
        long timeouts = probeLong(probe, "wave_timeouts");
        String json = "{\n"
                + "  \"pass\": " + pass + ",\n"
                + "  \"reason\": \"" + reason + "\",\n"
                + "  \"ticks\": " + ticks + ",\n"
                + "  \"splits\": " + splits + ",\n"
                + "  \"force_partitions\": " + force + ",\n"
                + "  \"split_misses\": " + misses + ",\n"
                + "  \"pulses_queued\": " + queued + ",\n"
                + "  \"pulses_ran\": " + ran + ",\n"
                + "  \"drained_blocks\": " + drainedBlocks + ",\n"
                + "  \"ran_blocks\": " + ranBlocks + ",\n"
                + "  \"slipped\": " + slipped + ",\n"
                + "  \"slipped_delta\": " + (slipped - slippedBefore) + ",\n"
                + "  \"wave_timeouts\": " + timeouts + "\n"
                + "}\n";
        try {
            Path out = Path.of("yap-partition-cut.json");
            Files.writeString(out, json, StandardCharsets.UTF_8);
            String home = System.getProperty("yapcore.home",
                    System.getProperty("yap.bench.root", "."));
            Path copy = Path.of(home).resolve("bench").resolve("partition-cut.json");
            Files.createDirectories(copy.getParent());
            Files.writeString(copy, json, StandardCharsets.UTF_8);
            plugin.getLogger().info("partition-cut " + (pass ? "PASS" : "FAIL")
                    + " reason=" + reason + " → " + out.toAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().severe("partition-cut result write failed: " + e.getMessage());
        }
        YapSched.global(plugin, Bukkit::shutdown);
    }
}
