package com.yapcore.factions;

import java.time.Instant;

/** Links a playerdata {@code claims.id} row to a faction without altering the claims schema. */
public record FactionClaimOverlay(long claimId, long factionId, int powerCost, Instant linkedAt) {
}
