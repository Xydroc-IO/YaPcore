package com.yapcore.playerdata.claims;

import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Optional;

/** Claim / subdivision creation from shovel selection. */
final class ClaimCreationOps {

    private final ClaimService host;

    ClaimCreationOps(ClaimService host) {
        this.host = host;
    }

    String createTopLevel(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        if (area < host.config().claimsMinArea()) {
            return "§cClaim too small (min " + host.config().claimsMinArea() + ").";
        }
        if (area > host.config().claimsMaxArea()) {
            return "§cClaim too large (max " + host.config().claimsMaxArea() + ").";
        }
        synchronized (host.localClaimsMutable()) {
            for (Claim c : host.localClaimsMutable()) {
                if (c.isSubdivision()) {
                    continue;
                }
                if (c.overlaps(world, minX, maxX, minZ, maxZ)) {
                    return "§cOverlaps existing claim #" + c.id();
                }
            }
        }
        int blocks = host.repo().getBlocks(player.getUniqueId(), host.config().claimsStartingBlocks());
        if (blocks < area) {
            return "§cNeed " + area + " claim blocks (you have " + blocks + ").";
        }
        Claim draft = Claim.topLevel(0, player.getUniqueId(), host.config().serverId(), world,
                minX, maxX, minZ, maxZ, player.getName() + "'s claim");
        long id = host.repo().create(draft);
        host.repo().setBlocks(player.getUniqueId(), blocks - area);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, draft.name(), null, 0, false);
        synchronized (host.localClaimsMutable()) {
            host.localClaimsMutable().add(created);
        }
        ClaimVisualizer.show(host.plugin(), player, created, host.config().claimsVisualSeconds());
        host.modesMutable().put(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
        return "§aClaim §f#" + id + " §acreated (" + area + " blocks). Remaining: " + (blocks - area);
    }

    String createSubdivision(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        if (area < host.config().claimsSubMinArea()) {
            return "§cSubdivision too small (min " + host.config().claimsSubMinArea() + ").";
        }
        // parent must contain both corners — use center of rect
        Location mid = new Location(Bukkit.getWorld(world), (minX + maxX) / 2.0, 64, (minZ + maxZ) / 2.0);
        Optional<Claim> top = host.getTopLevelAt(mid);
        if (top.isEmpty() || !top.get().world().equals(world)) {
            host.modesMutable().put(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
            return "§cStand inside your claim to subdivide. Mode reset to claim.";
        }
        Claim parent = top.get();
        if (!parent.owner().equals(player.getUniqueId())
                && !host.hasTrust(parent, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE)
                && !StaffBypass.land(player)) {
            return "§cYou need manage trust on the parent claim.";
        }
        if (!parent.containsFully(minX, maxX, minZ, maxZ)) {
            return "§cSubdivision must be fully inside claim #" + parent.id();
        }
        synchronized (host.localClaimsMutable()) {
            for (Claim c : host.localClaimsMutable()) {
                if (!c.isSubdivision() || c.parentId() != parent.id()) {
                    continue;
                }
                if (c.overlaps(world, minX, maxX, minZ, maxZ)) {
                    return "§cOverlaps subdivision #" + c.id();
                }
            }
        }
        Claim draft = new Claim(0, parent.owner(), host.config().serverId(), world,
                minX, maxX, minZ, maxZ, "Sub of #" + parent.id(), parent.id(), 0, false);
        long id = host.repo().create(draft);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, draft.name(), parent.id(), 0, false);
        synchronized (host.localClaimsMutable()) {
            host.localClaimsMutable().add(created);
        }
        ClaimVisualizer.show(host.plugin(), player, created, host.config().claimsVisualSeconds());
        host.modesMutable().put(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
        return "§aSubdivision §f#" + id + " §ainside claim §f#" + parent.id()
                + " §a(" + area + " blocks). Mode back to claim.";
    }
}

