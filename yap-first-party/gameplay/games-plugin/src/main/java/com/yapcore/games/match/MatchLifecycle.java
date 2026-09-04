package com.yapcore.games.match;

import com.yapcore.games.MatchId;
import com.yapcore.games.MatchState;
import com.yapcore.games.arena.ArenaDefinition;
import com.yapcore.games.kit.KitDefinition;
import com.yapcore.games.kit.KitSnapshot;
import com.yapcore.games.mode.GameModeDefinition;
import com.yapcore.mmo.CombatServices;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Match start / end / restore helpers for MatchManager. */
final class MatchLifecycle {

    private final MatchManager host;

    MatchLifecycle(MatchManager host) {
        this.host = host;
    }

    void startDirectMatch(GameModeDefinition mode, List<UUID> playerIds) {
        ArenaDefinition arena = host.arenas().get(mode.arenaId());
        KitDefinition kit = host.kitLoader().get(mode.kitId());
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
        host.matches().put(match.id(), match);
        int spawnIndex = 0;
        JavaPlugin plugin = host.plugin();
        for (UUID id : playerIds) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            match.addPlayer(id, KitSnapshot.capture(player));
            host.playerMatch().put(id, match.id());
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
                : host.config().defaultCountdown();
        match.setCountdownEndsAtMs(System.currentTimeMillis() + seconds * 1000L);
        broadcast(match, "§eMatch starts in §f" + seconds + "§e…");
        host.ui().runCountdown(match, () -> startLive(match));
    }

    private void startLive(Match match) {
        if (match.state() != MatchState.COUNTDOWN) {
            return;
        }
        match.setState(MatchState.LIVE);
        JavaPlugin plugin = host.plugin();
        if (match.mode().durationSeconds() > 0) {
            match.setLiveEndsAtMs(System.currentTimeMillis() + match.mode().durationSeconds() * 1000L);
            YapSched.globalLater(plugin, () -> {
                if (match.state() == MatchState.LIVE) {
                    endMatch(match, resolveWinner(match));
                }
            }, match.mode().durationSeconds() * 20L);
            YapSched.globalTimer(plugin, () -> {
                if (match.state() == MatchState.LIVE) {
                    host.ui().refreshScoreboard(match);
                }
            }, 20L, 20L);
        }
        broadcast(match, "§aFight!");
        host.ui().showFight(match);
    }

    void eliminateToSpectator(Player player, Match match) {
        match.eliminate(player.getUniqueId());
        if (host.config().spectatorsOnElimination()) {
            match.addSpectator(player.getUniqueId());
            YapSched.entity(host.plugin(), player, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§7You were eliminated. Spectating…");
            });
        }
    }

    void endMatch(Match match, UUID winnerId) {
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
        host.rewards().payWinner(winnerId, match.mode().type());
        recordStats(match, winnerId);
        for (UUID id : new ArrayList<>(match.players())) {
            restorePlayer(id, match);
            host.playerMatch().remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                host.ui().clear(player);
            }
        }
        host.resetter().clearDrops(match.arena());
        host.matches().remove(match.id());
    }

    private void recordStats(Match match, UUID winnerId) {
        YapSched.async(host.plugin(), () -> {
            for (UUID id : match.players()) {
                try {
                    host.stats().recordMatch(id, match.mode().id(),
                            match.kills(id),
                            match.deaths(id),
                            winnerId != null && winnerId.equals(id));
                } catch (Exception e) {
                    host.plugin().getLogger().log(Level.WARNING, "stats record", e);
                }
            }
        });
    }

    void restorePlayer(UUID playerId, Match match) {
        Player player = Bukkit.getPlayer(playerId);
        KitSnapshot snapshot = match.snapshots().get(playerId);
        if (player == null || snapshot == null) {
            return;
        }
        snapshot.restore(host.plugin(), player);
        resetCombatHp(player);
    }

    void resetCombatHp(Player player) {
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

    private void broadcast(Match match, String message) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
