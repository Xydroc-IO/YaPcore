package com.yapcore.conquest;

import java.util.Optional;

/**
 * Pure zone rules when {@code zones.enabled} is on.
 * Empty optional = do not override (leave to faction claim rules or server default).
 */
public final class ConquestZoneRules {

    public record Policy(
            boolean claimable,
            Optional<Boolean> build,
            Optional<Boolean> pvp,
            Optional<Boolean> explode) {
    }

    public record Settings(
            boolean warzoneClaimable,
            boolean warzoneBuild,
            boolean warzonePvp,
            boolean warzoneExplode,
            boolean safezoneClaimable,
            boolean safezoneBuild,
            boolean safezonePvp,
            boolean safezoneExplode,
            boolean wildernessClaimable,
            boolean wildernessBuildOverride,
            boolean wildernessBuild,
            boolean wildernessPvpOverride,
            boolean wildernessPvp,
            boolean wildernessExplodeOverride,
            boolean wildernessExplode) {
    }

    private ConquestZoneRules() {
    }

    public static Policy policy(ConquestZoneType type, Settings settings) {
        return switch (type) {
            case WARZONE -> new Policy(
                    settings.warzoneClaimable(),
                    Optional.of(settings.warzoneBuild()),
                    Optional.of(settings.warzonePvp()),
                    Optional.of(settings.warzoneExplode()));
            case SAFEZONE -> new Policy(
                    settings.safezoneClaimable(),
                    Optional.of(settings.safezoneBuild()),
                    Optional.of(settings.safezonePvp()),
                    Optional.of(settings.safezoneExplode()));
            case WILDERNESS -> new Policy(
                    settings.wildernessClaimable(),
                    settings.wildernessBuildOverride()
                            ? Optional.of(settings.wildernessBuild())
                            : Optional.empty(),
                    settings.wildernessPvpOverride()
                            ? Optional.of(settings.wildernessPvp())
                            : Optional.empty(),
                    settings.wildernessExplodeOverride()
                            ? Optional.of(settings.wildernessExplode())
                            : Optional.empty());
        };
    }

    public static boolean canClaim(ConquestZoneType type, Settings settings) {
        return policy(type, settings).claimable();
    }

    /** Unclaimed zone build override. Empty = no conquest override. */
    public static Optional<Boolean> evaluateBuildUnclaimed(ConquestZoneType type, Settings settings) {
        return policy(type, settings).build();
    }

    /**
     * Merge faction-land PvP with zone. Safezone always blocks; warzone can force allow.
     * When unclaimed, uses zone policy only.
     */
    public static Optional<Boolean> evaluatePvp(
            ConquestZoneType type, Settings settings, boolean claimed, Optional<Boolean> factionPvp) {
        Policy p = policy(type, settings);
        if (type == ConquestZoneType.SAFEZONE) {
            return Optional.of(false);
        }
        if (type == ConquestZoneType.WARZONE && p.pvp().orElse(true)) {
            return Optional.of(true);
        }
        if (claimed) {
            return factionPvp;
        }
        return p.pvp();
    }

    public static Optional<Boolean> evaluateExplode(ConquestZoneType type, Settings settings) {
        return policy(type, settings).explode();
    }

    /**
     * Claimed land build: safezone denies everyone; otherwise faction result.
     * Unclaimed: zone policy.
     */
    public static Optional<Boolean> evaluateBuild(
            ConquestZoneType type, Settings settings, boolean claimed, Optional<Boolean> factionBuild) {
        if (type == ConquestZoneType.SAFEZONE) {
            return Optional.of(false);
        }
        if (claimed) {
            return factionBuild;
        }
        return evaluateBuildUnclaimed(type, settings);
    }
}
