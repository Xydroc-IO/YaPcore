package com.yapcore.claims;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

final class ClaimListenerExplosion implements Listener {

    private final ClaimService claims;

    ClaimListenerExplosion(ClaimService claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(event.getEntity());
        if (kind == ClaimExplosionRules.Kind.NONE) {
            return;
        }
        if (!explosionAllowed(kind, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(event.getEntity());
        if (kind == ClaimExplosionRules.Kind.NONE) {
            return;
        }
        org.bukkit.Location origin = event.getLocation();
        boolean originDenied = origin != null && !explosionAllowed(kind, origin);
        boolean anyClaimBlockDenied = false;
        var blocks = event.blockList().iterator();
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (block == null || !explosionAllowed(kind, block.getLocation())) {
                blocks.remove();
                anyClaimBlockDenied = true;
            }
        }
        if (originDenied || (anyClaimBlockDenied && event.blockList().isEmpty())) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakExplosion(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return;
        }
        Entity remover = event instanceof HangingBreakByEntityEvent byEntity
                ? byEntity.getRemover() : null;
        ClaimExplosionRules.Kind kind = ClaimExplosionRules.kindOf(remover);
        if (kind == ClaimExplosionRules.Kind.NONE) {
            if (!claims.isCreeperExplosionAllowed(event.getEntity().getLocation())
                    || !claims.isTntAllowed(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!explosionAllowed(kind, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }
        if (!claims.isFireSpreadAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean explosionAllowed(ClaimExplosionRules.Kind kind, org.bukkit.Location location) {
        return switch (kind) {
            case TNT -> claims.isTntAllowed(location);
            case CREEPER -> claims.isCreeperExplosionAllowed(location);
            case NONE -> true;
        };
    }
}
