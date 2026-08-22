package com.yapcore.games.match;

import com.yapcore.games.GameModeId;
import com.yapcore.games.GamesConfig;
import com.yapcore.games.MatchId;
import com.yapcore.games.MatchState;
import com.yapcore.games.MatchView;
import com.yapcore.games.PlayerGameStats;
import com.yapcore.games.QueueStatus;
import com.yapcore.games.arena.ArenaDefinition;
import com.yapcore.games.arena.ArenaLoader;
import com.yapcore.games.db.StatsRepository;
import com.yapcore.games.economy.MatchRewards;
import com.yapcore.games.kit.KitDefinition;
import com.yapcore.games.kit.KitLoader;
import com.yapcore.games.kit.KitSnapshot;
import com.yapcore.games.mode.GameModeDefinition;
import com.yapcore.games.mode.GameModeLoader;
import com.yapcore.games.mode.GameModeType;
import com.yapcore.games.reset.ArenaResetter;
import com.yapcore.mmo.CombatServices;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class MatchManager {

    private final JavaPlugin plugin;
    private final GamesConfig config;
    private final GameModeLoader modeLoader;
    private final ArenaLoader arenaLoader;
    private final KitLoader kitLoader;
    private final ArenaResetter resetter;
    private final StatsRepository stats;
    private final MatchRewards rewards;
    private final MatchUi ui;

    private final Map<GameModeId, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, GameModeId> queued = new ConcurrentHashMap<>();
    private final Map<UUID, MatchId> playerMatch = new ConcurrentHashMap<>();
    private final Map<MatchId, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingDuels = new ConcurrentHashMap<>();

    public MatchManager(
            JavaPlugin plugin,
            GamesConfig config,
            GameModeLoader modeLoader,
            ArenaLoader arenaLoader,
            KitLoader kitLoader,
            ArenaResetter resetter,
            StatsRepository stats,
            MatchRewards rewards,
            MatchUi ui) {
        this.plugin = plugin;
        this.config = config;
        this.modeLoader = modeLoader;
        this.arenaLoader = arenaLoader;
        this.kitLoader = kitLoader;
        this.resetter = resetter;
        this.stats = stats;
        this.rewards = rewards;
        this.ui = ui;
    }

    public Collection<MatchView> activeMatches() {
        return matches.values().stream().map(Match::view).toList();
    }

    public Optional<Match> matchOf(UUID playerId) {
        MatchId id = playerMatch.get(playerId);
        return id == null ? Optional.empty() : Optional.ofNullable(matches.get(id));
    }

    public boolean isInMatch(UUID playerId) {
        return playerMatch.containsKey(playerId);
    }

    public boolean isInActiveMatch(UUID playerId) {
        return matchOf(playerId)
                .map(m -> m.state() == MatchState.COUNTDOWN || m.state() == MatchState.LIVE)
                .orElse(false);
    }

    public boolean suppressesSkillXp(UUID playerId) {
        return config.blockSkillXp() && isInActiveMatch(playerId);
    }

    public Optional<QueueStatus> queueStatus(UUID playerId) {
        GameModeId modeId = queued.get(playerId);
        if (modeId == null) {
            return Optional.empty();
        }
        Deque<UUID> queue = queues.get(modeId);
        if (queue == null) {
            return Optional.empty();
        }
        int pos = 1;
        for (UUID id : queue) {
            if (id.equals(playerId)) {
                return Optional.of(new QueueStatus(modeId, pos, queue.size()));
            }
            pos++;
        }
        return Optional.empty();
    }

    public boolean allowPvp(Player attacker, Player victim) {
        Optional<Match> atk = matchOf(attacker.getUniqueId());
        Optional<Match> vic = matchOf(victim.getUniqueId());
        if (atk.isEmpty() || vic.isEmpty()) {
            return false;
        }
        Match match = atk.get();
        if (!match.id().equals(vic.get().id())) {
            return false;
        }
        return match.state() == MatchState.LIVE
                && match.alive().contains(attacker.getUniqueId())
                && match.alive().contains(victim.getUniqueId());
    }

    public boolean joinQueue(UUID playerId, GameModeId modeId) {
        if (queued.containsKey(playerId) || playerMatch.containsKey(playerId)) {
            return false;
        }
        GameModeDefinition mode = modeLoader.get(modeId);
        if (mode == null) {
            return false;
        }
        Deque<UUID> queue = queues.computeIfAbsent(modeId, k -> new ArrayDeque<>());
        queue.addLast(playerId);
        queued.put(playerId, modeId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            var status = queueStatus(playerId).orElse(new QueueStatus(modeId, queue.size(), queue.size()));
            player.sendMessage("§aQueued for §f" + mode.displayName()
                    + "§a (#" + status.position() + " of " + status.queueSize() + ").");
        }
        tryStartMatch(mode, false);
        return true;
    }

    public boolean leaveQueue(UUID playerId) {
        GameModeId modeId = queued.remove(playerId);
        if (modeId == null) {
            return false;
        }
        Deque<UUID> queue = queues.get(modeId);
        if (queue != null) {
            queue.remove(playerId);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§7Left the queue.");
        }
        return true;
    }

    public boolean leaveMatch(UUID playerId) {
        Match match = matchOf(playerId).orElse(null);
        if (match == null) {
            return false;
        }
        match.eliminate(playerId);
        restorePlayer(playerId, match);
        playerMatch.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            ui.clear(player);
            player.sendMessage("§7You left the match.");
        }
        if (match.state() == MatchState.LIVE || match.state() == MatchState.COUNTDOWN) {
            if (match.mode().type() != GameModeType.FFA && match.alive().size() <= 1) {
                endMatch(match, match.alive().stream().findFirst().orElse(null));
            } else if (match.state() == MatchState.COUNTDOWN && match.players().size() <= 1) {
                matches.remove(match.id());
            }
        }
        return true;
    }

    public boolean forceStart(GameModeId modeId) {
        GameModeDefinition mode = modeLoader.get(modeId);
        if (mode == null) {
            return false;
        }
        Deque<UUID> queue = queues.get(modeId);
        if (queue == null || queue.isEmpty()) {
            return false;
        }
        tryStartMatch(mode, true);
        return true;
    }

    public Optional<PlayerGameStats> loadStats(UUID playerId, GameModeId mode) {
        try {
            return stats.get(playerId, mode);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "stats load", e);
            return Optional.empty();
        }
    }

    public boolean challengeDuel(UUID challenger, UUID target) {
        if (challenger.equals(target)) {
            return false;
        }
        if (queued.containsKey(challenger) || playerMatch.containsKey(challenger)) {
            return false;
        }
        if (queued.containsKey(target) || playerMatch.containsKey(target)) {
            return false;
        }
        pendingDuels.put(target, challenger);
        Player targetPlayer = Bukkit.getPlayer(target);
        Player challengerPlayer = Bukkit.getPlayer(challenger);
        if (targetPlayer != null && challengerPlayer != null) {
            targetPlayer.sendMessage("§e" + challengerPlayer.getName()
                    + " §7challenged you. §a/duel " + challengerPlayer.getName() + " §7to accept.");
            challengerPlayer.sendMessage("§aDuel challenge sent to §f" + targetPlayer.getName() + "§a.");
        }
        return true;
    }

    public boolean acceptDuel(UUID accepter, UUID challenger) {
        UUID pending = pendingDuels.get(accepter);
        if (pending == null || !pending.equals(challenger)) {
            return false;
        }
        pendingDuels.remove(accepter);
        GameModeDefinition mode = modeLoader.get(GameModeId.of("duels"));
        if (mode == null) {
            return false;
        }
        startDirectMatch(mode, List.of(challenger, accepter));
        return true;
    }

    private void tryStartMatch(GameModeDefinition mode, boolean force) {
        Deque<UUID> queue = queues.get(mode.id());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        if (!force && queue.size() < mode.minPlayers()) {
            return;
        }
        List<UUID> picked = new ArrayList<>();
        while (!queue.isEmpty() && picked.size() < mode.maxPlayers()) {
            UUID next = queue.pollFirst();
            if (next != null && Bukkit.getPlayer(next) != null) {
                queued.remove(next);
                picked.add(next);
            }
        }
        int required = force ? Math.min(1, mode.minPlayers()) : mode.minPlayers();
        if (picked.size() < Math.max(1, required)) {
            for (UUID id : picked) {
                queue.addFirst(id);
                queued.put(id, mode.id());
            }
            return;
        }
        startDirectMatch(mode, picked);
    }

    private void startDirectMatch(GameModeDefinition mode, List<UUID> playerIds) {
        ArenaDefinition arena = arenaLoader.get(mode.arenaId());
        KitDefinition kit = kitLoader.get(mode.kitId());
        if (arena == null || kit == null) {
            for (UUID id : playerIds) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage("§cMatch setup failed — missing arena or kit.");
                }
            }
            return;
        }
        Match match = new Match(MatchId.random(), mode, arena);
        matches.put(match.id(), match);
        int spawnIndex = 0;
        for (UUID id : playerIds) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            match.addPlayer(id, KitSnapshot.capture(player));
            playerMatch.put(id, match.id());
            final int idx = spawnIndex++;
            YapSched.entity(plugin, player, () -> {
                org.bukkit.World world = plugin.getServer().getWorld(arena.worldName());
                if (world != null) {
                    player.teleport(arena.randomSpawn(world, idx));
                }
                KitSnapshot.applyKitSync(player, kit);
            });
            resetCombatHp(player);
        }
        beginCountdown(match);
    }

    private void beginCountdown(Match match) {
        match.setState(MatchState.COUNTDOWN);
        int seconds = match.mode().countdownSeconds() > 0
                ? match.mode().countdownSeconds()
                : config.defaultCountdown();
        match.setCountdownEndsAtMs(System.currentTimeMillis() + seconds * 1000L);
        broadcast(match, "§eMatch starts in §f" + seconds + "§e…");
        ui.runCountdown(match, () -> startLive(match));
    }

    private void startLive(Match match) {
        if (match.state() != MatchState.COUNTDOWN) {
            return;
        }
        match.setState(MatchState.LIVE);
        if (match.mode().durationSeconds() > 0) {
            match.setLiveEndsAtMs(System.currentTimeMillis() + match.mode().durationSeconds() * 1000L);
            YapSched.globalLater(plugin, () -> {
                if (match.state() == MatchState.LIVE) {
                    endMatch(match, resolveWinner(match));
                }
            }, match.mode().durationSeconds() * 20L);
            YapSched.globalTimer(plugin, () -> {
                if (match.state() == MatchState.LIVE) {
                    ui.refreshScoreboard(match);
                }
            }, 20L, 20L);
        }
        broadcast(match, "§aFight!");
        ui.showFight(match);
    }

    public void handleDeath(Player victim, Player killer) {
        Match match = matchOf(victim.getUniqueId()).orElse(null);
        if (match == null || match.state() != MatchState.LIVE) {
            return;
        }
        if (killer != null && match.players().contains(killer.getUniqueId())) {
            match.recordKill(killer.getUniqueId());
            killer.sendMessage("§a+1 kill (§f" + match.kills(killer.getUniqueId()) + "§a)");
            ui.refreshScoreboard(match);
            if (match.mode().type() == GameModeType.FFA
                    && match.kills(killer.getUniqueId()) >= match.mode().winKills()) {
                endMatch(match, killer.getUniqueId());
                return;
            }
        }
        match.recordDeath(victim.getUniqueId());
        if (match.mode().type() == GameModeType.DUEL) {
            UUID winner = killer != null ? killer.getUniqueId() : null;
            endMatch(match, winner);
            return;
        }
        if (match.mode().respawnInArena()) {
            KitDefinition kit = kitLoader.get(match.mode().kitId());
            if (kit != null) {
                YapSched.entityLater(plugin, victim, () -> {
                    victim.spigot().respawn();
                    YapSched.entityLater(plugin, victim, () -> {
                        org.bukkit.World world = plugin.getServer().getWorld(match.arena().worldName());
                        if (world != null) {
                            victim.teleport(match.arena().randomSpawn(world, 0));
                        }
                        KitSnapshot.applyKitSync(victim, kit);
                        resetCombatHp(victim);
                    }, 2L);
                }, 1L);
            }
            return;
        }
        eliminateToSpectator(victim, match);
        if (match.alive().size() <= 1) {
            endMatch(match, match.alive().stream().findFirst().orElse(null));
        }
    }

    private void eliminateToSpectator(Player player, Match match) {
        match.eliminate(player.getUniqueId());
        if (config.spectatorsOnElimination()) {
            match.addSpectator(player.getUniqueId());
            YapSched.entity(plugin, player, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§7You were eliminated. Spectating…");
            });
        }
    }

    public void handleQuit(UUID playerId) {
        leaveQueue(playerId);
        pendingDuels.entrySet().removeIf(e -> e.getKey().equals(playerId) || e.getValue().equals(playerId));
        if (!playerMatch.containsKey(playerId)) {
            return;
        }
        leaveMatch(playerId);
    }

    private void endMatch(Match match, UUID winnerId) {
        if (match.state() == MatchState.ENDING) {
            return;
        }
        match.setState(MatchState.ENDING);
        match.setWinner(winnerId);
        String winMsg = winnerId == null
                ? "§7Match ended."
                : "§6" + Optional.ofNullable(Bukkit.getPlayer(winnerId)).map(Player::getName).orElse("Unknown")
                + " §ewins!";
        broadcast(match, winMsg);
        rewards.payWinner(winnerId, match.mode().type());
        recordStats(match, winnerId);
        for (UUID id : new ArrayList<>(match.players())) {
            restorePlayer(id, match);
            playerMatch.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                ui.clear(player);
            }
        }
        resetter.clearDrops(match.arena());
        matches.remove(match.id());
    }

    private void recordStats(Match match, UUID winnerId) {
        YapSched.async(plugin, () -> {
            for (UUID id : match.players()) {
                try {
                    stats.recordMatch(id, match.mode().id(),
                            match.kills(id),
                            match.deaths(id),
                            winnerId != null && winnerId.equals(id));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "stats record", e);
                }
            }
        });
    }

    private void restorePlayer(UUID playerId, Match match) {
        Player player = Bukkit.getPlayer(playerId);
        KitSnapshot snapshot = match.snapshots().get(playerId);
        if (player == null || snapshot == null) {
            return;
        }
        snapshot.restore(plugin, player);
        resetCombatHp(player);
    }

    private void resetCombatHp(Player player) {
        CombatServices.find().ifPresent(combat -> {
            try {
                combat.setHp(player.getUniqueId(), combat.stats(player).hitpoints()).join();
            } catch (Exception ignored) {
            }
        });
    }

    private UUID resolveWinner(Match match) {
        UUID best = null;
        int bestKills = -1;
        for (UUID id : match.players()) {
            int k = match.kills(id);
            if (k > bestKills) {
                bestKills = k;
                best = id;
            }
        }
        if (bestKills <= 0 && match.alive().size() == 1) {
            return match.alive().iterator().next();
        }
        return best;
    }

    public GameModeLoader modes() {
        return modeLoader;
    }

    public ArenaLoader arenas() {
        return arenaLoader;
    }

    public Map<String, Object> dashboardSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("activeMatches", matches.size());
        Map<String, Integer> queueSizes = new LinkedHashMap<>();
        for (var entry : queues.entrySet()) {
            queueSizes.put(entry.getKey().id(), entry.getValue().size());
        }
        snap.put("queueSizes", queueSizes);
        snap.put("modes", modeLoader.modes().keySet().stream().map(GameModeId::id).toList());
        snap.put("arenas", arenaLoader.arenas().keySet());
        return snap;
    }

    private void broadcast(Match match, String message) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
