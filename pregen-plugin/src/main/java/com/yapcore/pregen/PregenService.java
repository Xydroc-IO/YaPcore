package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;
import com.yapcore.pregen.shape.ChunkShape;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PregenService {

    private final JavaPlugin plugin;
    private final PregenConfig config;
    private final ProgressStore store;
    private final Logger log;
    private final Map<String, PregenJob> jobs = new ConcurrentHashMap<>();
    private YapTask pump;
    private YapTask persistTask;

    public PregenService(JavaPlugin plugin, PregenConfig config, ProgressStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.log = plugin.getLogger();
    }

    public void start() {
        pump = YapSched.globalTimer(plugin, this::tick, 1L, 1L);
        persistTask = YapSched.globalTimer(plugin, this::persistRunning, 20L * 15, 20L * 15);
        if (config.autoResume()) {
            for (ProgressStore.SavedJob saved : store.loadAll()) {
                World w = Bukkit.getWorld(saved.world());
                if (w == null) {
                    log.warning("Skip resume for missing world: " + saved.world());
                    continue;
                }
                if (jobs.size() >= config.maxWorlds()) {
                    break;
                }
                PregenJob job = new PregenJob(
                        saved.world(),
                        saved.shape() + " (resumed)",
                        saved.remaining(),
                        saved.done(),
                        saved.total());
                jobs.put(saved.world().toLowerCase(), job);
                log.info("Resumed pregen: " + job.statusLine());
            }
        }
    }

    public void shutdown() {
        if (pump != null) {
            pump.cancel();
        }
        if (persistTask != null) {
            persistTask.cancel();
        }
        for (PregenJob job : jobs.values()) {
            if (job.state() == PregenJob.State.RUNNING || job.state() == PregenJob.State.PAUSED) {
                store.save(job);
            }
        }
        jobs.clear();
    }

    public synchronized String startJob(World world, ChunkShape shape) {
        String key = world.getName().toLowerCase();
        PregenJob existing = jobs.get(key);
        if (existing != null && (existing.state() == PregenJob.State.RUNNING
                || existing.state() == PregenJob.State.PAUSED)) {
            return "Already running for " + world.getName() + ": " + existing.statusLine();
        }
        long active = jobs.values().stream()
                .filter(j -> j.state() == PregenJob.State.RUNNING || j.state() == PregenJob.State.PAUSED)
                .count();
        if (active >= config.maxWorlds()) {
            return "Max worlds (" + config.maxWorlds() + ") already pregenerating";
        }
        List<ChunkPos> coords = ChunkShape.materialize(shape);
        if (coords.isEmpty()) {
            return "Shape produced 0 chunks";
        }
        PregenJob job = new PregenJob(world.getName(), shape.description(), coords);
        jobs.put(key, job);
        store.save(job);
        broadcast("Started pregen: " + job.statusLine());
        return "Started: " + job.statusLine();
    }

    public String pause(String worldOrAll) {
        return setState(worldOrAll, PregenJob.State.PAUSED, PregenJob.State.RUNNING);
    }

    public String resume(String worldOrAll) {
        return setState(worldOrAll, PregenJob.State.RUNNING, PregenJob.State.PAUSED);
    }

    public String cancel(String worldOrAll) {
        List<String> msgs = new ArrayList<>();
        for (PregenJob job : match(worldOrAll)) {
            job.setState(PregenJob.State.CANCELLED);
            store.delete(job.worldName());
            jobs.remove(job.worldName().toLowerCase());
            msgs.add("Cancelled " + job.worldName());
            broadcast("Cancelled pregen for " + job.worldName());
        }
        return msgs.isEmpty() ? "No matching jobs" : String.join("; ", msgs);
    }

    public String status(String worldOrAll) {
        Collection<PregenJob> list = match(worldOrAll);
        if (list.isEmpty()) {
            return "No pregen jobs";
        }
        StringBuilder sb = new StringBuilder();
        for (PregenJob job : list) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(job.statusLine());
        }
        return sb.toString();
    }

    public Map<String, Object> statusMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> arr = new ArrayList<>();
        for (PregenJob job : jobs.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", job.worldName());
            m.put("state", job.state().name());
            m.put("done", job.done());
            m.put("total", job.total());
            m.put("remaining", job.remainingCount());
            m.put("percent", job.progressPercent());
            m.put("rate", job.ratePerSecond());
            m.put("shape", job.shapeDescription());
            arr.add(m);
        }
        out.put("jobs", arr);
        out.put("mspt", Bukkit.getServer().getAverageTickTime() / 1_000_000.0);
        return out;
    }

    private String setState(String worldOrAll, PregenJob.State to, PregenJob.State from) {
        List<String> msgs = new ArrayList<>();
        for (PregenJob job : match(worldOrAll)) {
            if (job.state() == from || (to == PregenJob.State.RUNNING && job.state() == PregenJob.State.PAUSED)
                    || (to == PregenJob.State.PAUSED && job.state() == PregenJob.State.RUNNING)) {
                job.setState(to);
                store.save(job);
                msgs.add(to + " " + job.worldName());
                broadcast(to + " pregen: " + job.worldName());
            }
        }
        return msgs.isEmpty() ? "No matching jobs in expected state" : String.join("; ", msgs);
    }

    private Collection<PregenJob> match(String worldOrAll) {
        if (worldOrAll == null || worldOrAll.isBlank() || "all".equalsIgnoreCase(worldOrAll)) {
            return new ArrayList<>(jobs.values());
        }
        PregenJob j = jobs.get(worldOrAll.toLowerCase());
        return j == null ? List.of() : List.of(j);
    }

    private void tick() {
        double mspt = Bukkit.getServer().getAverageTickTime() / 1_000_000.0;
        if (mspt > config.maxMspt()) {
            return;
        }
        int globalInflight = jobs.values().stream().mapToInt(PregenJob::inflight).sum();
        int budget = Math.max(0, config.maxInflight() - globalInflight);
        if (budget <= 0) {
            return;
        }
        int perTick = config.chunksPerTick();
        int issued = 0;
        List<PregenJob> running = jobs.values().stream()
                .filter(j -> j.state() == PregenJob.State.RUNNING)
                .toList();
        if (running.isEmpty()) {
            return;
        }
        int roundRobin = 0;
        while (issued < perTick && issued < budget) {
            PregenJob job = running.get(roundRobin % running.size());
            roundRobin++;
            if (job.state() != PregenJob.State.RUNNING) {
                if (roundRobin > running.size() * 2) {
                    break;
                }
                continue;
            }
            World world = Bukkit.getWorld(job.worldName());
            if (world == null) {
                job.setState(PregenJob.State.CANCELLED);
                continue;
            }
            ChunkPos next = job.poll();
            if (next == null) {
                if (job.inflight() == 0) {
                    finish(job);
                }
                if (roundRobin > running.size() * 2) {
                    break;
                }
                continue;
            }
            job.beginInflight();
            final ChunkPos pos = next;
            final PregenJob j = job;
            world.getChunkAtAsync(pos.x(), pos.z(), true, chunk ->
                    YapSched.region(plugin, world, pos.x() << 4, pos.z() << 4, () -> {
                        j.endInflightSuccess();
                        if (j.isQueueEmpty() && j.state() == PregenJob.State.RUNNING) {
                            finish(j);
                        }
                    }));
            issued++;
            maybeBroadcast(job);
            if (roundRobin > running.size() * perTick + 4) {
                break;
            }
        }
    }

    private void finish(PregenJob job) {
        job.setState(PregenJob.State.DONE);
        store.delete(job.worldName());
        jobs.remove(job.worldName().toLowerCase());
        broadcast("Pregen complete: " + job.worldName() + " " + job.done() + "/" + job.total());
        log.info("Pregen complete: " + job.statusLine());
    }

    private void maybeBroadcast(PregenJob job) {
        long now = System.currentTimeMillis();
        if (now - job.lastBroadcastMs() >= config.broadcastIntervalSec() * 1000L) {
            job.markBroadcast();
            broadcast(job.statusLine());
        }
    }

    private void persistRunning() {
        for (PregenJob job : jobs.values()) {
            if (job.state() == PregenJob.State.RUNNING || job.state() == PregenJob.State.PAUSED) {
                store.save(job);
            }
        }
    }

    private void broadcast(String msg) {
        String line = "[YaPPregen] " + msg;
        log.info(msg);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("yappregen.admin"))
                .forEach(p -> p.sendMessage(line));
        Bukkit.getConsoleSender().sendMessage(line);
    }

    public Optional<PregenJob> get(String world) {
        return Optional.ofNullable(jobs.get(world.toLowerCase()));
    }
}
