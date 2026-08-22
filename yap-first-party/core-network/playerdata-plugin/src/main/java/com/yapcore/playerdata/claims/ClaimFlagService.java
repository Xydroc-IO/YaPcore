package com.yapcore.playerdata.claims;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimFlagService {

    private final ClaimFlagRepository repo;
    private final PlayerDataConfig config;
    private final Map<Long, Map<RegionFlag, FlagValue>> cache = new ConcurrentHashMap<>();

    public ClaimFlagService(ClaimFlagRepository repo, PlayerDataConfig config) {
        this.repo = repo;
        this.config = config;
    }

    public Optional<FlagValue> explicit(long claimId, RegionFlag flag) {
        Map<RegionFlag, FlagValue> flags = cache.computeIfAbsent(claimId, this::loadSafe);
        return Optional.ofNullable(flags.get(flag));
    }

    public FlagValue resolveOrDefault(long claimId, RegionFlag flag) {
        return explicit(claimId, flag).orElseGet(() -> config.defaultClaimFlag(flag));
    }

    public void setFlag(long claimId, RegionFlag flag, FlagValue value) throws SQLException {
        repo.set(claimId, flag, value);
        cache.computeIfAbsent(claimId, id -> new EnumMap<>(RegionFlag.class)).put(flag, value);
    }

    public Map<RegionFlag, FlagValue> flagsFor(long claimId) {
        return Map.copyOf(cache.computeIfAbsent(claimId, this::loadSafe));
    }

    public void invalidate(long claimId) {
        cache.remove(claimId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Map<RegionFlag, FlagValue> loadSafe(long claimId) {
        try {
            return new EnumMap<>(repo.load(claimId));
        } catch (SQLException e) {
            return new EnumMap<>(RegionFlag.class);
        }
    }
}
