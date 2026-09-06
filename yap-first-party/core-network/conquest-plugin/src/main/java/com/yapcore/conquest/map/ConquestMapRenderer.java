package com.yapcore.conquest.map;

import com.yapcore.conquest.ConquestChunk;
import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.ConquestZoneType;
import com.yapcore.conquest.service.ConquestServiceImpl;
import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionServices;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConquestMapRenderer {

    private ConquestMapRenderer() {
    }

    public static List<String> render(Player player, ConquestServiceImpl conquest, ConquestConfig config) {
        int radius = config.mapRadius();
        var origin = player.getLocation();
        int originCx = origin.getBlockX() >> 4;
        int originCz = origin.getBlockZ() >> 4;
        String world = origin.getWorld() == null ? "" : origin.getWorld().getName();
        Optional<Faction> viewer = FactionServices.find()
                .flatMap(fs -> fs.findByPlayer(player.getUniqueId()));

        List<String> lines = new ArrayList<>();
        lines.add("§6Conquest map §7(1 chunk/cell)");
        for (int row = radius; row >= -radius; row--) {
            StringBuilder sb = new StringBuilder();
            for (int col = -radius; col <= radius; col++) {
                if (col == 0 && row == 0) {
                    sb.append("§e*");
                    continue;
                }
                int cx = originCx + col;
                int cz = originCz + row;
                char symbol = symbolAt(world, cx, cz, viewer.orElse(null), conquest, config);
                sb.append(colorFor(symbol)).append(symbol);
            }
            lines.add(sb.toString());
        }
        if (config.zonesEnabled()) {
            lines.add("§8Legend: §e*§7you §a+§7own §b=§7ally §c-§7enemy §8#§7other"
                    + " §cW§7war §aS§7safe §8.§7wild");
        } else {
            lines.add("§8Legend: §e*§7you §a+§7own §b=§7ally §c-§7enemy §8#§7other §8.§7wild");
        }
        return lines;
    }

    private static String colorFor(char symbol) {
        return switch (symbol) {
            case '+' -> "§a";
            case '=' -> "§b";
            case '-' -> "§c";
            case 'W' -> "§c";
            case 'S' -> "§a";
            case '#' -> "§8";
            default -> "§7";
        };
    }

    private static char symbolAt(
            String world,
            int cx,
            int cz,
            Faction viewer,
            ConquestServiceImpl conquest,
            ConquestConfig config) {
        Optional<ConquestChunk> chunk = conquest.chunk(world, cx, cz);
        if (chunk.isPresent()) {
            if (viewer != null && chunk.get().factionId() == viewer.id()) {
                return '+';
            }
            if (viewer == null) {
                return '#';
            }
            FactionRelation rel = FactionServices.find()
                    .map(fs -> fs.relationBetween(viewer.id(), chunk.get().factionId()))
                    .orElse(FactionRelation.NEUTRAL);
            return switch (rel) {
                case ALLY -> '=';
                case ENEMY -> '-';
                case NEUTRAL -> '#';
            };
        }
        if (config.zonesEnabled()) {
            ConquestZoneType zone = conquest.zone(world, cx, cz).orElse(ConquestZoneType.WILDERNESS);
            return switch (zone) {
                case WARZONE -> 'W';
                case SAFEZONE -> 'S';
                case WILDERNESS -> '.';
            };
        }
        return '.';
    }
}
