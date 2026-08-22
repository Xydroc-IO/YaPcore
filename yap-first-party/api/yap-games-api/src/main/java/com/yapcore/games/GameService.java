package com.yapcore.games;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GameService {

    Collection<GameModeId> modes();

    Collection<MatchView> activeMatches();

    Optional<MatchView> currentMatch(UUID playerId);

    Optional<QueueStatus> queueStatus(UUID playerId);

    boolean isInActiveMatch(UUID playerId);

    boolean isInMatch(UUID playerId);

    /** Block skill XP while in an active match when configured. */
    boolean suppressesSkillXp(UUID playerId);

    /** True when yap-combat should allow PvP between these players. */
    boolean allowPvp(Player attacker, Player victim);

    CompletableFuture<Boolean> joinQueue(UUID playerId, GameModeId mode);

    CompletableFuture<Boolean> leaveQueue(UUID playerId);

    CompletableFuture<Boolean> leaveMatch(UUID playerId);

    /** Accepter confirms a pending challenge from challenger. */
    CompletableFuture<Boolean> acceptDuel(UUID accepter, UUID challenger);

    CompletableFuture<Optional<PlayerGameStats>> stats(UUID playerId, GameModeId mode);

    CompletableFuture<Boolean> forceStart(GameModeId mode);
}
