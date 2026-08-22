package com.yapcore.games.cmd;

import com.yapcore.games.GamesPlugin;
import com.yapcore.games.GameModeId;
import com.yapcore.games.match.MatchManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class YGamesCommand implements CommandExecutor, TabCompleter {

    private final GamesPlugin plugin;
    private final MatchManager matches;

    public YGamesCommand(GamesPlugin plugin, MatchManager matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapgames.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /ygames <reload|list|forcestart|info|snapshot json>");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadGames();
                sender.sendMessage("§aYaPGames reloaded — modes=" + plugin.modeCount()
                        + " arenas=" + plugin.arenaCount());
                yield true;
            }
            case "list" -> {
                sender.sendMessage("§6Modes:");
                matches.modes().modes().values().forEach(m ->
                        sender.sendMessage(" §7- §f" + m.id().id() + " §7(" + m.displayName()
                                + ", arena=" + m.arenaId() + ", kit=" + m.kitId() + ")"));
                sender.sendMessage("§6Arenas: §f" + matches.arenas().arenas().keySet());
                sender.sendMessage("§6Active matches: §f" + matches.activeMatches().size());
                yield true;
            }
            case "forcestart" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /ygames forcestart <mode>");
                    yield true;
                }
                GameModeId mode = GameModeId.of(args[1]);
                if (matches.forceStart(mode)) {
                    sender.sendMessage("§aForce-started §f" + mode.id() + "§a.");
                } else {
                    sender.sendMessage("§cCould not force-start (empty queue or unknown mode).");
                }
                yield true;
            }
            case "info" -> {
                sender.sendMessage("§6Active matches: §f" + matches.activeMatches().size());
                matches.activeMatches().forEach(v ->
                        sender.sendMessage(" §7" + v.matchId() + " §f" + v.mode().id()
                                + " §7" + v.state() + " players=" + v.players().size()));
                yield true;
            }
            case "snapshot" -> {
                if (args.length < 2 || !"json".equalsIgnoreCase(args[1])) {
                    sender.sendMessage("Usage: /ygames snapshot json");
                    yield true;
                }
                sender.sendMessage("YAPGAMES_JSON:" + toFlatJson(matches.dashboardSnapshot()));
                yield true;
            }
            default -> {
                sender.sendMessage("Usage: /ygames <reload|list|forcestart|info|snapshot json>");
                yield true;
            }
        };
    }

    private static String toFlatJson(java.util.Map<String, Object> snap) {
        return snap.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                .collect(Collectors.joining("|"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "list", "forcestart", "info", "snapshot").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcestart")) {
            return matches.modes().modes().keySet().stream()
                    .map(GameModeId::id)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("snapshot")
                && "json".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("json");
        }
        return List.of();
    }
}
