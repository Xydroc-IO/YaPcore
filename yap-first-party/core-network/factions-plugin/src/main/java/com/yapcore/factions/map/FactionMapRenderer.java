package com.yapcore.factions.map;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.integration.ClaimIntegration;
import com.yapcore.factions.service.FactionServiceImpl;
import com.yapcore.playerdata.claims.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FactionMapRenderer {

    private FactionMapRenderer() {
    }

    public static List<String> render(Player player, FactionServiceImpl factions, FactionsConfig config) {
        int radius = config.mapRadius();
        int cell = config.mapCellBlocks();
        Location origin = player.getLocation();
        Optional<Faction> viewerFaction = factions.findByPlayer(player.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add("§6Faction map §7(" + cell + "m/cell)");
        for (int row = radius; row >= -radius; row--) {
            StringBuilder sb = new StringBuilder();
            for (int col = -radius; col <= radius; col++) {
                int x = origin.getBlockX() + col * cell;
                int z = origin.getBlockZ() + row * cell;
                Location sample = new Location(origin.getWorld(), x, origin.getY(), z);
                char symbol = symbolAt(sample, viewerFaction.orElse(null), factions);
                if (col == 0 && row == 0) {
                    sb.append("§e").append(symbol).append("§7");
                } else {
                    sb.append("§7").append(symbol);
                }
            }
            lines.add(sb.toString());
        }
        lines.add("§8Legend: §e*§7you §a+§7own §b=§7ally §c-§7enemy §8.§7wild");
        return lines;
    }

    private static char symbolAt(Location loc, Faction viewerFaction, FactionServiceImpl factions) {
        Optional<Claim> claim = ClaimIntegration.claimAt(loc);
        if (claim.isEmpty()) {
            return '.';
        }
        var overlay = factions.overlayForClaim(claim.get().id());
        if (overlay.isEmpty()) {
            return '.';
        }
        if (viewerFaction != null && overlay.get().factionId() == viewerFaction.id()) {
            return '+';
        }
        if (viewerFaction == null) {
            return '#';
        }
        FactionRelation rel = factions.relationBetween(viewerFaction.id(), overlay.get().factionId());
        return switch (rel) {
            case ALLY -> '=';
            case ENEMY -> '-';
            case NEUTRAL -> '#';
        };
    }
}
