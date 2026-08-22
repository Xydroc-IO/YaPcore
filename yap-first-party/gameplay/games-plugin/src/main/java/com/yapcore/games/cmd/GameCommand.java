package com.yapcore.games.cmd;

import com.yapcore.games.GameModeId;
import com.yapcore.games.match.MatchManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class GameCommand implements CommandExecutor, TabCompleter {

    private final MatchManager matches;

    public GameCommand(MatchManager matches) {
        this.matches = matches;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapgames.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("Usage: /game <leave|status|stats [mode]>");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "leave" -> {
                if (matches.leaveMatch(player.getUniqueId()) || matches.leaveQueue(player.getUniqueId())) {
                    yield true;
                }
                player.sendMessage("§cYou are not in a queue or match.");
                yield true;
            }
            case "status" -> {
                var match = matches.matchOf(player.getUniqueId());
                if (match.isPresent()) {
                    player.sendMessage("§6Match: §f" + match.get().mode().displayName()
                            + " §7(" + match.get().state() + ")");
                } else {
                    matches.queueStatus(player.getUniqueId()).ifPresentOrElse(
                            q -> player.sendMessage("§6Queue: §f" + q.mode().id()
                                    + " §7#" + q.position() + "/" + q.queueSize()),
                            () -> player.sendMessage("§7Not in queue or match."));
                }
                yield true;
            }
            case "stats" -> {
                GameModeId mode = args.length >= 2
                        ? GameModeId.of(args[1])
                        : GameModeId.of("ffa");
                matches.loadStats(player.getUniqueId(), mode).ifPresentOrElse(
                        s -> player.sendMessage("§6" + mode.id() + " §7— wins §f" + s.wins()
                                + "§7, kills §f" + s.kills() + "§7, deaths §f" + s.deaths()),
                        () -> player.sendMessage("§7No stats for §f" + mode.id() + "§7."));
                yield true;
            }
            default -> {
                player.sendMessage("Usage: /game <leave|status|stats [mode]>");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("leave", "status", "stats").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return List.of("ffa", "duels").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
