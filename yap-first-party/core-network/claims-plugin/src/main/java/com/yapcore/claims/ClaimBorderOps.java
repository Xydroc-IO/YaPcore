package com.yapcore.claims;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Continuous claim-border particle view and nearby owned-claim queries. */
final class ClaimBorderOps {

    private final ClaimService host;

    ClaimBorderOps(ClaimService host) {
        this.host = host;
    }

    /** Toggle continuous particle borders for owned claims. Returns new on/off state. */
    boolean toggleBorderView(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Set<UUID> borderView = host.borderViewMutable();
        if (!borderView.add(uuid)) {
            borderView.remove(uuid);
            return false;
        }
        return true;
    }

    boolean borderViewEnabled(UUID uuid) {
        return uuid != null && host.borderViewMutable().contains(uuid);
    }

    void clearBorderView(UUID uuid) {
        if (uuid != null) {
            host.borderViewMutable().remove(uuid);
        }
    }

    void tickBorderView() {
        Set<UUID> borderView = host.borderViewMutable();
        if (borderView.isEmpty()) {
            return;
        }
        for (UUID id : Set.copyOf(borderView)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                borderView.remove(id);
                continue;
            }
            YapSched.entity(host.plugin(), player, () -> {
                if (!player.isOnline() || !borderView.contains(id)) {
                    return;
                }
                List<Claim> near = ownedNear(player, 96);
                if (!near.isEmpty()) {
                    ClaimVisualizer.drawFrame(player, near);
                }
            });
        }
    }

    /** Owned claims on this world whose footprint is within {@code radius} blocks of the player. */
    List<Claim> ownedNear(Player player, int radius) {
        if (player.getWorld() == null) {
            return List.of();
        }
        String world = player.getWorld().getName();
        UUID owner = player.getUniqueId();
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        int r = Math.max(16, radius);
        List<Claim> out = new ArrayList<>();
        synchronized (host.localClaimsMutable()) {
            for (Claim c : host.localClaimsMutable()) {
                if (!c.owner().equals(owner) || !world.equals(c.world())) {
                    continue;
                }
                int cx = (c.minX() + c.maxX()) / 2;
                int cz = (c.minZ() + c.maxZ()) / 2;
                int dx = Math.abs(cx - px);
                int dz = Math.abs(cz - pz);
                // also include if player is inside or within radius of any edge
                if (c.contains(world, px, pz)
                        || (dx <= r + c.width() / 2 && dz <= r + c.length() / 2)) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
