package com.yapcore.games.cmd;

import com.yapcore.games.GameModeId;
import com.yapcore.games.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class QueueCommand implements CommandExecutor, TabCompleter {

    private final MatchManager matches;

    public QueueCommand(MatchManager matches) {
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
            player.sendMessage("Usage: /queue <ffa|duels|leave>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("leave")) {
            matches.leaveQueue(player.getUniqueId());
            return true;
        }
        GameModeId mode = GameModeId.of(sub.equals("duel") ? "duels" : sub);
        if (!matches.joinQueue(player.getUniqueId(), mode)) {
            player.sendMessage("§cCould not join queue (already queued/in match or unknown mode).");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("ffa", "duels", "leave").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
