package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonRun;
import com.yapcore.dungeons.DungeonRunState;
import org.bukkit.Location;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Mutable live run tracked in memory. */
public final class LiveRun {

    private final String runId;
    private final int dungeonLevel;
    private final UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final String worldName;
    private final long seed;
    private volatile DungeonRunState state;
    private final AtomicInteger lives;
    private final int maxLives;
    private final Instant startedAt;
    private volatile Instant endedAt;
    private volatile Location entrance;
    private volatile Instant lastOccupiedAt = Instant.now();
    private final AtomicInteger deaths = new AtomicInteger();

    public LiveRun(
            String runId,
            int dungeonLevel,
            UUID leader,
            String worldName,
            long seed,
            DungeonRunState state,
            int lives,
            int maxLives) {
        this.runId = runId;
        this.dungeonLevel = dungeonLevel;
        this.leader = leader;
        this.worldName = worldName;
        this.seed = seed;
        this.state = state;
        this.lives = new AtomicInteger(lives);
        this.maxLives = maxLives;
        this.startedAt = Instant.now();
        this.members.add(leader);
    }

    public DungeonRun snapshot() {
        return new DungeonRun(
                runId, dungeonLevel, leader, Set.copyOf(members), worldName, seed,
                state, lives.get(), maxLives, startedAt, endedAt);
    }

    public String runId() {
        return runId;
    }

    public int dungeonLevel() {
        return dungeonLevel;
    }

    public UUID leader() {
        return leader;
    }

    public Set<UUID> members() {
        return members;
    }

    public String worldName() {
        return worldName;
    }

    public long seed() {
        return seed;
    }

    public DungeonRunState state() {
        return state;
    }

    public void setState(DungeonRunState state) {
        this.state = state;
        if (state == DungeonRunState.CLEARED || state == DungeonRunState.FAILED || state == DungeonRunState.CLEANING) {
            endedAt = Instant.now();
        }
    }

    public int lives() {
        return lives.get();
    }

    public int maxLives() {
        return maxLives;
    }

    public int consumeLife() {
        return lives.decrementAndGet();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Location entrance() {
        return entrance;
    }

    public void setEntrance(Location entrance) {
        this.entrance = entrance;
    }

    public Instant lastOccupiedAt() {
        return lastOccupiedAt;
    }

    public void touchOccupied() {
        lastOccupiedAt = Instant.now();
    }

    public int deaths() {
        return deaths.get();
    }

    public void addDeath() {
        deaths.incrementAndGet();
    }

    public boolean isMember(UUID id) {
        return leader.equals(id) || members.contains(id);
    }

    public boolean isTerminal() {
        return state == DungeonRunState.CLEARED
                || state == DungeonRunState.FAILED
                || state == DungeonRunState.CLEANING;
    }
}
