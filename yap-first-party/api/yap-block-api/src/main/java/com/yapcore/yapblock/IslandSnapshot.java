package com.yapcore.yapblock;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable view of an island for API consumers. */
public final class IslandSnapshot {

    private final long id;
    private final UUID ownerId;
    private final String name;
    private final int gridX;
    private final int gridZ;
    private final double homeX;
    private final double homeY;
    private final double homeZ;
    private final int sizeRadius;
    private final int maxMembers;
    private final int genTier;
    private final long level;
    private final Instant createdAt;
    private final Map<IslandFlag, Boolean> flags;

    public IslandSnapshot(
            long id,
            UUID ownerId,
            String name,
            int gridX,
            int gridZ,
            double homeX,
            double homeY,
            double homeZ,
            int sizeRadius,
            int maxMembers,
            int genTier,
            long level,
            Instant createdAt,
            Map<IslandFlag, Boolean> flags) {
        this.id = id;
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.name = name == null || name.isBlank() ? "Island" : name;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.sizeRadius = sizeRadius;
        this.maxMembers = maxMembers;
        this.genTier = genTier;
        this.level = level;
        this.createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        EnumMap<IslandFlag, Boolean> copy = new EnumMap<>(IslandFlag.class);
        if (flags != null) {
            copy.putAll(flags);
        }
        for (IslandFlag flag : IslandFlag.values()) {
            copy.putIfAbsent(flag, flag.defaultValue());
        }
        this.flags = Collections.unmodifiableMap(copy);
    }

    public long id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public int gridX() {
        return gridX;
    }

    public int gridZ() {
        return gridZ;
    }

    public double homeX() {
        return homeX;
    }

    public double homeY() {
        return homeY;
    }

    public double homeZ() {
        return homeZ;
    }

    public int sizeRadius() {
        return sizeRadius;
    }

    public int maxMembers() {
        return maxMembers;
    }

    public int genTier() {
        return genTier;
    }

    public long level() {
        return level;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<IslandFlag, Boolean> flags() {
        return flags;
    }

    public boolean flag(IslandFlag flag) {
        return flags.getOrDefault(flag, flag.defaultValue());
    }

    public IslandSnapshot withLevel(long newLevel) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                sizeRadius, maxMembers, genTier, newLevel, createdAt, flags);
    }

    public IslandSnapshot withHome(double x, double y, double z) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, x, y, z,
                sizeRadius, maxMembers, genTier, level, createdAt, flags);
    }

    public IslandSnapshot withSizeRadius(int radius) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                radius, maxMembers, genTier, level, createdAt, flags);
    }

    public IslandSnapshot withMaxMembers(int members) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                sizeRadius, members, genTier, level, createdAt, flags);
    }

    public IslandSnapshot withGenTier(int tier) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                sizeRadius, maxMembers, tier, level, createdAt, flags);
    }

    public IslandSnapshot withFlags(Map<IslandFlag, Boolean> newFlags) {
        return new IslandSnapshot(id, ownerId, name, gridX, gridZ, homeX, homeY, homeZ,
                sizeRadius, maxMembers, genTier, level, createdAt, newFlags);
    }

    public IslandSnapshot withFlag(IslandFlag flag, boolean value) {
        EnumMap<IslandFlag, Boolean> next = new EnumMap<>(flags);
        next.put(flag, value);
        return withFlags(next);
    }
}
