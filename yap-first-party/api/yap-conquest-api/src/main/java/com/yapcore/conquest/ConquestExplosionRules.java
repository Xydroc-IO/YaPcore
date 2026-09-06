package com.yapcore.conquest;

import java.util.Optional;

/** Pure explosion policy for claimed land (zones still handled separately). */
public final class ConquestExplosionRules {

    public enum ClaimedPolicy {
        DENY,
        ALLOW;

        public static ClaimedPolicy parse(String raw) {
            if (raw == null) {
                return DENY;
            }
            return switch (raw.trim().toLowerCase()) {
                case "allow", "true", "yes" -> ALLOW;
                default -> DENY;
            };
        }
    }

    private ConquestExplosionRules() {
    }

    /**
     * Merge zone explode override with claimed-land policy.
     * Zone deny (safezone) always wins. When explosions feature off, return zone only.
     */
    public static Optional<Boolean> evaluate(
            boolean explosionsEnabled,
            boolean claimed,
            ClaimedPolicy claimedPolicy,
            Optional<Boolean> zoneExplode) {
        if (zoneExplode.isPresent() && !zoneExplode.get()) {
            return Optional.of(false);
        }
        if (!explosionsEnabled) {
            return zoneExplode;
        }
        if (claimed) {
            return Optional.of(claimedPolicy == ClaimedPolicy.ALLOW);
        }
        if (zoneExplode.isPresent()) {
            return zoneExplode;
        }
        return Optional.empty();
    }
}
