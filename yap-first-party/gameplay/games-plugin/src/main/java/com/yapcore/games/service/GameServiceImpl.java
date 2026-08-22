package com.yapcore.games.service;

import com.yapcore.games.GameModeId;
import com.yapcore.games.GameService;
import com.yapcore.games.MatchView;
import com.yapcore.games.PlayerGameStats;
import com.yapcore.games.QueueStatus;
import com.yapcore.games.match.Match;
import com.yapcore.games.match.MatchManager;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GameServiceImpl implements GameService {

    private final MatchManager matches;
    private final Collection<GameModeId> modes;

    public GameServiceImpl(MatchManager matches, Collection<GameModeId> modes) {
        this.matches = matches;
        this.modes = modes;
    }

    @Override
    public Collection<GameModeId> modes() {
        return modes;
    }

    @Override
    public Collection<MatchView> activeMatches() {
        return matches.activeMatches();
    }

    @Override
    public Optional<MatchView> currentMatch(UUID playerId) {
        return matches.matchOf(playerId).map(Match::view);
    }

    @Override
    public Optional<QueueStatus> queueStatus(UUID playerId) {
        return matches.queueStatus(playerId);
    }

    @Override
    public boolean isInActiveMatch(UUID playerId) {
        return matches.isInActiveMatch(playerId);
    }

    @Override
    public boolean isInMatch(UUID playerId) {
        return matches.isInMatch(playerId);
    }

    @Override
    public boolean suppressesSkillXp(UUID playerId) {
        return matches.suppressesSkillXp(playerId);
    }

    @Override
    public boolean allowPvp(Player attacker, Player victim) {
        return matches.allowPvp(attacker, victim);
    }

    @Override
    public CompletableFuture<Boolean> joinQueue(UUID playerId, GameModeId mode) {
        return CompletableFuture.completedFuture(matches.joinQueue(playerId, mode));
    }

    @Override
    public CompletableFuture<Boolean> leaveQueue(UUID playerId) {
        return CompletableFuture.completedFuture(matches.leaveQueue(playerId));
    }

    @Override
    public CompletableFuture<Boolean> leaveMatch(UUID playerId) {
        return CompletableFuture.completedFuture(matches.leaveMatch(playerId));
    }

    @Override
    public CompletableFuture<Boolean> acceptDuel(UUID accepter, UUID challenger) {
        return CompletableFuture.completedFuture(matches.acceptDuel(accepter, challenger));
    }

    @Override
    public CompletableFuture<Optional<PlayerGameStats>> stats(UUID playerId, GameModeId mode) {
        return CompletableFuture.completedFuture(matches.loadStats(playerId, mode));
    }

    @Override
    public CompletableFuture<Boolean> forceStart(GameModeId mode) {
        return CompletableFuture.completedFuture(matches.forceStart(mode));
    }
}
