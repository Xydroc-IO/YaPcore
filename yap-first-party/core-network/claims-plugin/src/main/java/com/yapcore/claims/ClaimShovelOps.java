package com.yapcore.claims;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Optional;

/** Shovel selection, chunk claim, and adjacent plot expand. */
final class ClaimShovelOps {

    private final ClaimService host;
    private final ClaimCreationOps creation;

    ClaimShovelOps(ClaimService host, ClaimCreationOps creation) {
        this.host = host;
        this.creation = creation;
    }

    String handleShovel(Player player, Location loc) throws SQLException {
        if (!host.config().claimsEnabled()) {
            return "§cClaims are disabled.";
        }
        ClaimService.SelectMode mode = host.mode(player.getUniqueId());
        if (mode != ClaimService.SelectMode.SUBDIVIDE
                && host.config().claimsMode() == ClaimsConfig.ClaimMode.CHUNK) {
            return claimChunkAt(player, loc);
        }
        ClaimService.Corner first = host.pendingCorner(player.getUniqueId());
        if (first == null) {
            host.setPendingCorner(player.getUniqueId(),
                    new ClaimService.Corner(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ()));
            String tip = mode == ClaimService.SelectMode.SUBDIVIDE ? "subdivision" : "claim";
            return "§a" + tip + " corner #1 set. Click opposite corner with shovel.";
        }
        if (!first.world().equals(loc.getWorld().getName())) {
            host.clearPendingCorner(player.getUniqueId());
            return "§cCorners must be in the same world. Selection cleared.";
        }
        int minX = Math.min(first.x(), loc.getBlockX());
        int maxX = Math.max(first.x(), loc.getBlockX());
        int minZ = Math.min(first.z(), loc.getBlockZ());
        int maxZ = Math.max(first.z(), loc.getBlockZ());
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);
        host.clearPendingCorner(player.getUniqueId());

        if (mode == ClaimService.SelectMode.SUBDIVIDE) {
            return creation.createSubdivision(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
        }
        return creation.createTopLevel(player, loc.getWorld().getName(), minX, maxX, minZ, maxZ, area);
    }

    String claimChunkAt(Player player, Location loc) throws SQLException {
        if (loc == null || loc.getWorld() == null) {
            return "§cInvalid location.";
        }
        int size = Math.max(1, host.config().claimsPlotSize());
        ClaimExpandRules.Plot plot = ClaimExpandRules.plotAt(loc.getBlockX(), loc.getBlockZ(), size);
        int maxY = loc.getWorld().getMaxHeight() - 1;
        int minY = Math.max(loc.getWorld().getMinHeight(), loc.getBlockY() - host.config().claimsPlotDepth());
        int area = size * size;
        host.clearPendingCorner(player.getUniqueId());
        return creation.createTopLevel(player, loc.getWorld().getName(),
                plot.minX(), plot.maxX(), plot.minZ(), plot.maxZ(), minY, maxY, area);
    }

    String expandAdjacent(Player player, ClaimExpandRules.Dir dir) throws SQLException {
        if (player == null || player.getWorld() == null) {
            return "§cInvalid location.";
        }
        if (host.config().claimsMode() != ClaimsConfig.ClaimMode.CHUNK) {
            return "§c/claim expand needs chunk/plot mode (claims.mode: chunk).";
        }
        int size = Math.max(1, host.config().claimsPlotSize());
        Location feet = player.getLocation();
        ClaimExpandRules.Plot here = ClaimExpandRules.plotAt(feet.getBlockX(), feet.getBlockZ(), size);
        ClaimExpandRules.Plot target = ClaimExpandRules.adjacent(here, dir, size);

        Claim neighbor = null;
        for (Claim c : host.localClaims()) {
            if (c.isSubdivision() || !c.owner().equals(player.getUniqueId())) {
                continue;
            }
            if (!c.world().equals(feet.getWorld().getName())) {
                continue;
            }
            if (ClaimExpandRules.sharesEdgeOrOverlaps(
                    c.minX(), c.maxX(), c.minZ(), c.maxZ(),
                    target.minX(), target.maxX(), target.minZ(), target.maxZ())) {
                neighbor = c;
                break;
            }
        }
        if (neighbor == null) {
            return "§cNo owned claim touches the plot to the §f" + dir.label()
                    + "§c. Stand in / next to your claim, face the empty plot, then §f/claim expand§c.";
        }

        int probeY = Math.max(neighbor.minY(), Math.min(neighbor.maxY(), feet.getBlockY()));
        Location probe = new Location(feet.getWorld(),
                target.centerX() + 0.5, probeY, target.centerZ() + 0.5);
        Optional<Claim> existing = host.getAt(probe);
        if (existing.isPresent()) {
            Claim e = existing.get();
            if (e.owner().equals(player.getUniqueId())) {
                return "§eYou already own that plot (§f#" + e.id() + "§e).";
            }
            return "§cThat plot is already claimed (§f#" + e.id() + "§c).";
        }

        int area = size * size;
        host.clearPendingCorner(player.getUniqueId());
        String msg = creation.createTopLevel(player, feet.getWorld().getName(),
                target.minX(), target.maxX(), target.minZ(), target.maxZ(),
                neighbor.minY(), neighbor.maxY(), area);
        if (msg.startsWith("§aClaim")) {
            return "§aExpanded §f" + dir.label() + "§a — " + msg.substring(2);
        }
        return msg;
    }
}
