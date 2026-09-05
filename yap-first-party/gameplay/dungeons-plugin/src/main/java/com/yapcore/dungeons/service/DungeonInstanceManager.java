package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonRun;
import com.yapcore.dungeons.DungeonRunState;
import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.db.DungeonRepository;
import com.yapcore.dungeons.event.DungeonCompleteEvent;
import com.yapcore.dungeons.event.DungeonFailEvent;
import com.yapcore.dungeons.gen.DifficultyTable;
import com.yapcore.dungeons.gen.DungeonCarver;
import com.yapcore.dungeons.gen.RoomGraphBuilder;
import com.yapcore.dungeons.gen.ThemeTable;
import com.yapcore.dungeons.loot.LootService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class DungeonInstanceManager {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final DungeonRepository repository;
    private final DungeonWorldOps worlds;
    private final DifficultyTable difficulty;
    private final ThemeTable themes;
    private final DungeonCarver carver;
    private final LootService loot;
    private final RoomGraphBuilder graphBuilder = new RoomGraphBuilder();

    private final Map<String, LiveRun> byId = new ConcurrentHashMap<>();
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> byWorld = new ConcurrentHashMap<>();

    public DungeonInstanceManager(
            JavaPlugin plugin,
            DungeonsConfig config,
            DungeonRepository repository,
            DungeonWorldOps worlds,
            DifficultyTable difficulty,
            ThemeTable themes,
            DungeonCarver carver,
            LootService loot) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.worlds = worlds;
        this.difficulty = difficulty;
        this.themes = themes;
        this.carver = carver;
        this.loot = loot;
    }

    public void startGcTimer() {
        YapSched.globalTimer(plugin, () -> {
            Instant now = Instant.now();
            for (LiveRun run : List.copyOf(byId.values())) {
                if (run.state() == DungeonRunState.CLEANING) {
                    continue;
                }
                World w = Bukkit.getWorld(run.worldName());
                if (w != null && !w.getPlayers().isEmpty()) {
                    run.touchOccupied();
                    continue;
                }
                long idleMin = Duration.between(run.lastOccupiedAt(), now).toMinutes();
                if (run.isTerminal() || idleMin >= config.idleGcMinutes()) {
                    cleanup(run.runId(), "idle/empty");
                }
            }
        }, 20L * 30, 20L * 30);
    }

    public void recoverOrphans() {
        worlds.cleanupOrphanWorldFolders();
        try {
            for (String world : repository.openRunWorlds()) {
                if (Bukkit.getWorld(world) != null || world.startsWith("yd_")) {
                    worlds.delete(world);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "run recovery failed", e);
        }
    }

    public Optional<LiveRun> byPlayer(UUID id) {
        String runId = byPlayer.get(id);
        return runId == null ? Optional.empty() : Optional.ofNullable(byId.get(runId));
    }

    public Optional<LiveRun> byId(String runId) {
        return Optional.ofNullable(byId.get(runId));
    }

    public Optional<LiveRun> byWorld(String world) {
        String runId = byWorld.get(world);
        return runId == null ? Optional.empty() : Optional.ofNullable(byId.get(runId));
    }

    public List<DungeonRun> activeSnapshots() {
        List<DungeonRun> out = new ArrayList<>();
        for (LiveRun r : byId.values()) {
            if (!r.isTerminal()) {
                out.add(r.snapshot());
            }
        }
        return out;
    }

    public CompletableFuture<Optional<LiveRun>> createAndGenerate(Player leader, int level) {
        String runId = UUID.randomUUID().toString();
        String worldName = UnlockMath.worldNameFor(runId);
        long seed = (runId.hashCode() * 31L) ^ (level * 1_000_003L);
        int partyGuess = 1;
        int maxLives = UnlockMath.maxLives(partyGuess, config.baseLives(), config.livesPerExtraMember(), config.livesCap());
        LiveRun run = new LiveRun(runId, level, leader.getUniqueId(), worldName, seed,
                DungeonRunState.GENERATING, maxLives, maxLives);
        byId.put(runId, run);
        byPlayer.put(leader.getUniqueId(), runId);
        byWorld.put(worldName, runId);

        return worlds.createFlat(worldName, seed).thenCompose(ok -> {
            if (!ok) {
                forget(run);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            try {
                repository.insertRun(runId, level, leader.getUniqueId(), worldName, DungeonRunState.GENERATING, seed);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "insertRun", e);
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                forget(run);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            DifficultyTable.LevelDiff diff = difficulty.get(level);
            ThemeTable.Theme theme = themes.themeFor(level);
            RoomGraphBuilder.Layout layout = graphBuilder.build(seed, diff.rooms());
            List<Player> notify = List.of(leader);
            carver.notifyPlayers(notify, "Generating dungeon level " + level + "…");
            return carver.carve(world, layout, theme, diff, seed, runId, msg -> carver.notifyPlayers(notify, msg))
                    .thenApply(result -> {
                        run.setEntrance(result.entrance());
                        for (Location chestLoc : result.chestLocations()) {
                            if (chestLoc.getBlock().getState() instanceof org.bukkit.block.Chest chest) {
                                loot.fillChest(chest, level, seed);
                            }
                        }
                        run.setState(DungeonRunState.OPEN);
                        try {
                            repository.updateRunState(runId, DungeonRunState.OPEN, false);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "updateRunState", e);
                        }
                        YapSched.entity(plugin, leader, () -> {
                            Location ent = run.entrance();
                            if (ent != null) {
                                leader.teleport(ent);
                            }
                            leader.sendMessage("§aDungeon ready. Invite friends with §e/dungeon invite <player>");
                            run.setState(DungeonRunState.ACTIVE);
                        });
                        return Optional.of(run);
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "generation failed", ex);
                        cleanup(runId, "gen-fail");
                        return Optional.empty();
                    });
        });
    }

    public void addMember(LiveRun run, UUID playerId) {
        run.members().add(playerId);
        byPlayer.put(playerId, run.runId());
        int size = 1 + run.members().size(); // leader counted in members set already
        // members includes leader — size is members.size()
        size = run.members().size();
        int newMax = UnlockMath.maxLives(size, config.baseLives(), config.livesPerExtraMember(), config.livesCap());
        // Only increase lives when party grows (never reduce mid-run)
        while (run.maxLives() < newMax && run.lives() < newMax) {
            // can't mutate maxLives easily — leave starting max; lives stay
            break;
        }
    }

    public void removePlayer(UUID playerId, boolean teleportOut) {
        LiveRun run = byPlayer(playerId).orElse(null);
        if (run == null) {
            return;
        }
        byPlayer.remove(playerId);
        run.members().remove(playerId);
        if (teleportOut) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                World fb = Bukkit.getWorlds().getFirst();
                YapSched.entity(plugin, p, () -> p.teleport(fb.getSpawnLocation()));
            }
        }
        if (run.leader().equals(playerId) || run.members().isEmpty()) {
            // If leader left, fail when no online members remain
            boolean anyOnline = run.members().stream().anyMatch(id -> Bukkit.getPlayer(id) != null);
            if (!anyOnline) {
                fail(run.runId(), "party empty");
            }
        }
    }

    public void onBossKilled(LiveRun run) {
        if (run.state() == DungeonRunState.CLEARED || run.state() == DungeonRunState.FAILED) {
            return;
        }
        run.setState(DungeonRunState.CLEARED);
        long clearMs = Duration.between(run.startedAt(), Instant.now()).toMillis();
        try {
            repository.updateRunState(run.runId(), DungeonRunState.CLEARED, true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "clear state", e);
        }
        Bukkit.getPluginManager().callEvent(new DungeonCompleteEvent(run.snapshot(), clearMs));
        for (UUID id : run.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            YapSched.entity(plugin, p, () -> {
                try {
                    var progress = repository.getProgress(id);
                    repository.upsertProgress(UnlockMath.afterClear(progress, run.dungeonLevel()));
                    int today = repository.clearsToday(id, run.dungeonLevel());
                    repository.recordClear(id, run.dungeonLevel(), clearMs, 0);
                    var items = loot.rollClearRewards(run.dungeonLevel(), run.seed(), today);
                    double eco = loot.economyPayout(run.dungeonLevel(), today);
                    loot.grant(p, items, eco);
                    p.sendMessage("§aDungeon cleared! Time: §f" + (clearMs / 1000) + "s");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "clear rewards", e);
                }
            });
        }
        YapSched.globalLater(plugin, () -> cleanup(run.runId(), "cleared"), 20L * config.clearGcSeconds());
    }

    public void onPlayerDeath(LiveRun run, Player player) {
        run.addDeath();
        int left = run.consumeLife();
        player.sendMessage("§cParty lives remaining: §f" + Math.max(0, left));
        if (left <= 0) {
            fail(run.runId(), "lives exhausted");
        }
    }

    /** Called from PlayerRespawnEvent for members still in an active run. */
    public void onPlayerRespawn(LiveRun run, Player player) {
        if (run.isTerminal() || run.lives() <= 0) {
            return;
        }
        Location ent = run.entrance();
        if (ent != null) {
            YapSched.entity(plugin, player, () -> player.teleport(ent));
        }
    }

    public void fail(String runId, String reason) {
        LiveRun run = byId.get(runId);
        if (run == null || run.state() == DungeonRunState.FAILED || run.state() == DungeonRunState.CLEARED) {
            return;
        }
        run.setState(DungeonRunState.FAILED);
        try {
            repository.updateRunState(runId, DungeonRunState.FAILED, true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "fail state", e);
        }
        Bukkit.getPluginManager().callEvent(new DungeonFailEvent(run.snapshot(), reason));
        for (UUID id : List.copyOf(run.members())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                YapSched.entity(plugin, p, () -> p.sendMessage("§cDungeon failed: §7" + reason));
            }
            removePlayer(id, true);
        }
        YapSched.globalLater(plugin, () -> cleanup(runId, reason), 20L * 15);
    }

    public void cleanup(String runId, String reason) {
        LiveRun run = byId.get(runId);
        if (run == null) {
            return;
        }
        run.setState(DungeonRunState.CLEANING);
        for (UUID id : List.copyOf(run.members())) {
            byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().getName().equals(run.worldName())) {
                World fb = Bukkit.getWorlds().getFirst();
                YapSched.entity(plugin, p, () -> p.teleport(fb.getSpawnLocation()));
            }
        }
        byWorld.remove(run.worldName());
        byId.remove(runId);
        worlds.delete(run.worldName()).thenAccept(ok ->
                plugin.getLogger().info("Deleted dungeon world " + run.worldName() + " (" + reason + ") ok=" + ok));
        try {
            repository.updateRunState(runId, DungeonRunState.CLEANING, true);
        } catch (Exception ignored) {
        }
    }

    private void forget(LiveRun run) {
        byId.remove(run.runId());
        byPlayer.remove(run.leader());
        byWorld.remove(run.worldName());
    }
}
