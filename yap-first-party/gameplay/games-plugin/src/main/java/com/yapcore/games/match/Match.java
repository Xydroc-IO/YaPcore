package com.yapcore.games.match;

import com.yapcore.games.GameModeId;
import com.yapcore.games.MatchId;
import com.yapcore.games.MatchState;
import com.yapcore.games.MatchView;
import com.yapcore.games.arena.ArenaDefinition;
import com.yapcore.games.kit.KitSnapshot;
import com.yapcore.games.mode.GameModeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Match {

    private final MatchId id;
    private final GameModeDefinition mode;
    private final ArenaDefinition arena;
    private MatchState state = MatchState.WAITING;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, KitSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private long countdownEndsAtMs;
    private long liveEndsAtMs;
    private UUID winner;

    public Match(MatchId id, GameModeDefinition mode, ArenaDefinition arena) {
        this.id = id;
        this.mode = mode;
        this.arena = arena;
    }

    public MatchId id() {
        return id;
    }

    public GameModeDefinition mode() {
        return mode;
    }

    public ArenaDefinition arena() {
        return arena;
    }

    public MatchState state() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = state;
    }

    public Set<UUID> players() {
        return Collections.unmodifiableSet(players);
    }

    public Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    public Map<UUID, KitSnapshot> snapshots() {
        return snapshots;
    }

    public void addPlayer(UUID uuid, KitSnapshot snapshot) {
        players.add(uuid);
        alive.add(uuid);
        snapshots.put(uuid, snapshot);
        kills.putIfAbsent(uuid, 0);
        deaths.putIfAbsent(uuid, 0);
    }

    public void recordDeath(UUID uuid) {
        deaths.merge(uuid, 1, Integer::sum);
    }

    public int deaths(UUID uuid) {
        return deaths.getOrDefault(uuid, 0);
    }

    public void eliminate(UUID uuid) {
        alive.remove(uuid);
    }

    public Set<UUID> spectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public void addSpectator(UUID uuid) {
        spectators.add(uuid);
        alive.remove(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public void reinstate(UUID uuid) {
        if (players.contains(uuid)) {
            alive.add(uuid);
        }
    }

    public void recordKill(UUID killer) {
        kills.merge(killer, 1, Integer::sum);
    }

    public int kills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public UUID winner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }

    public long countdownEndsAtMs() {
        return countdownEndsAtMs;
    }

    public void setCountdownEndsAtMs(long countdownEndsAtMs) {
        this.countdownEndsAtMs = countdownEndsAtMs;
    }

    public long liveEndsAtMs() {
        return liveEndsAtMs;
    }

    public void setLiveEndsAtMs(long liveEndsAtMs) {
        this.liveEndsAtMs = liveEndsAtMs;
    }

    public MatchView view() {
        return new MatchView(id, mode.id(), state, arena.id(), Set.copyOf(players), alive.size());
    }
}
