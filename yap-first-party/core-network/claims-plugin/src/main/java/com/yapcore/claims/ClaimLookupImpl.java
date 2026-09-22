package com.yapcore.claims;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bridges {@link ClaimService} to the public {@link ClaimLookup} API. */
public final class ClaimLookupImpl implements ClaimLookup {

    private final ClaimService claims;

    public ClaimLookupImpl(ClaimService claims) {
        this.claims = claims;
    }

    @Override
    public boolean enabled() {
        return claims != null && claims.config().claimsEnabled();
    }

    @Override
    public Optional<ClaimInfo> at(Location location) {
        if (!enabled() || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return claims.getAt(location).map(ClaimLookupImpl::toInfo);
    }

    @Override
    public List<ClaimInfo> localClaims() {
        if (!enabled()) {
            return List.of();
        }
        List<ClaimInfo> out = new ArrayList<>();
        for (Claim c : claims.localClaims()) {
            out.add(toInfo(c));
        }
        return List.copyOf(out);
    }

    static ClaimInfo toInfo(Claim c) {
        return new ClaimInfo(
                c.id(),
                c.owner(),
                c.serverId(),
                c.world(),
                c.minX(),
                c.maxX(),
                c.minZ(),
                c.maxZ(),
                c.name(),
                c.parentId());
    }
}
