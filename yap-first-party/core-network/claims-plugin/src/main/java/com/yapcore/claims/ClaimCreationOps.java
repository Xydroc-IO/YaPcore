package com.yapcore.claims;

import com.yapcore.claims.db.ClaimRepository;
import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.PlayerDataServiceProvider;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Optional;

/** Claim / subdivision creation from shovel selection or plot claim. */
final class ClaimCreationOps {

    private final ClaimService host;

    ClaimCreationOps(ClaimService host) {
        this.host = host;
    }

    String createTopLevel(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        World w = Bukkit.getWorld(world);
        int minY = w == null ? -64 : w.getMinHeight();
        int maxY = w == null ? 319 : w.getMaxHeight() - 1;
        Location feet = player.getLocation();
        if (feet.getWorld() != null && feet.getWorld().getName().equals(world)) {
            minY = Math.max(feet.getWorld().getMinHeight(),
                    feet.getBlockY() - host.config().claimsPlotDepth());
            maxY = feet.getWorld().getMaxHeight() - 1;
        }
        return createTopLevel(player, world, minX, maxX, minZ, maxZ, minY, maxY, area);
    }

    String createTopLevel(Player player, String world, int minX, int maxX, int minZ, int maxZ,
                          int minY, int maxY, int area) throws SQLException {
        if (host.config().claimsWorldDenied(world)) {
            return "§cClaims are disabled in world §f" + world + "§c.";
        }
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

        int owned = countOwnedTopLevel(player);
        if (owned >= host.config().claimsMaxClaims()) {
            return "§cClaim limit reached (§f" + owned + "/" + host.config().claimsMaxClaims() + "§c).";
        }

        boolean useBlocks = host.config().claimsUseClaimBlocks();
        int blocks = 0;
        if (useBlocks) {
            blocks = host.repo().getBlocks(player.getUniqueId(), host.config().claimsStartingBlocks());
            if (blocks < area) {
                return "§cNeed " + area + " claim blocks (you have " + blocks + ").";
            }
        }

        double cost = host.config().claimsCostEnabled() ? host.config().claimsCostAmount() : 0;
        PlayerDataService eco = null;
        if (cost > 0) {
            eco = PlayerDataServiceProvider.find().orElse(null);
            if (eco == null) {
                return "§cYaPPlayerData economy required to buy claims.";
            }
            double bal = eco.balance(player.getUniqueId());
            if (bal < cost) {
                return "§cNeed §f$" + money(cost) + " §cto claim (you have §f$" + money(bal) + "§c).";
            }
        }

        if (cost > 0) {
            Optional<Double> after = eco.withdraw(player.getUniqueId(), cost);
            if (after.isEmpty()) {
                return "§cCould not charge §f$" + money(cost) + " §c(economy off or insufficient funds).";
            }
        }

        Claim draft = Claim.topLevel(0, player.getUniqueId(), host.config().serverId(), world,
                minX, maxX, minZ, maxZ, minY, maxY, player.getName() + "'s claim");
        long id;
        try {
            id = host.repo().create(draft);
        } catch (SQLException e) {
            if (cost > 0 && eco != null) {
                eco.deposit(player.getUniqueId(), cost);
            }
            throw e;
        }
        if (useBlocks) {
            host.repo().setBlocks(player.getUniqueId(), blocks - area);
        }
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, minY, maxY, draft.name(), null, 0, false);
        synchronized (host.localClaimsMutable()) {
            host.localClaimsMutable().add(created);
        }
        ClaimVisualizer.show(host.plugin(), player, created, host.config().claimsVisualSeconds());
        host.modesMutable().put(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
        int remainSlots = host.config().claimsMaxClaims() - owned - 1;
        String paid = cost > 0 ? " §7(paid §a$" + money(cost) + "§7)" : "";
        String size = chunkLabel(minX, maxX, minZ, maxZ)
                + " · Y " + minY + "→" + maxY;
        if (useBlocks) {
            return "§aClaim §f#" + id + " §acreated (" + area + " blocks)" + paid
                    + ". Remaining blocks: " + (blocks - area) + " · slots §f" + remainSlots;
        }
        return "§aClaim §f#" + id + " §acreated (" + size + ")"
                + paid + ". Slots left: §f" + remainSlots;
    }

    String createSubdivision(Player player, String world, int minX, int maxX, int minZ, int maxZ, int area)
            throws SQLException {
        if (area < host.config().claimsSubMinArea()) {
            return "§cSubdivision too small (min " + host.config().claimsSubMinArea() + ").";
        }
        Location mid = new Location(Bukkit.getWorld(world), (minX + maxX) / 2.0, 64, (minZ + maxZ) / 2.0);
        Optional<Claim> top = host.getTopLevelAtXZ(world, mid.getBlockX(), mid.getBlockZ());
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
                minX, maxX, minZ, maxZ, parent.minY(), parent.maxY(),
                "Sub of #" + parent.id(), parent.id(), 0, false);
        long id = host.repo().create(draft);
        Claim created = new Claim(id, draft.owner(), draft.serverId(), draft.world(),
                minX, maxX, minZ, maxZ, parent.minY(), parent.maxY(),
                draft.name(), parent.id(), 0, false);
        synchronized (host.localClaimsMutable()) {
            host.localClaimsMutable().add(created);
        }
        ClaimVisualizer.show(host.plugin(), player, created, host.config().claimsVisualSeconds());
        host.modesMutable().put(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
        return "§aSubdivision §f#" + id + " §aof claim §f#" + parent.id()
                + " §a(" + area + " blocks). Mode back to claim.";
    }

    int countOwnedTopLevel(Player player) throws SQLException {
        String server = host.config().serverId();
        int n = 0;
        for (Claim c : host.repo().listOwned(player.getUniqueId())) {
            if (!c.isSubdivision()
                    && c.serverId() != null
                    && c.serverId().equalsIgnoreCase(server)) {
                n++;
            }
        }
        return n;
    }

    private static String chunkLabel(int minX, int maxX, int minZ, int maxZ) {
        int w = maxX - minX + 1;
        int l = maxZ - minZ + 1;
        if (w == l) {
            return w + "×" + l;
        }
        return w + "×" + l + " (" + (w * l) + " blocks)";
    }

    static String money(double amount) {
        if (Math.rint(amount) == amount) {
            return String.format("%.0f", amount);
        }
        return String.format("%.2f", amount);
    }
}
