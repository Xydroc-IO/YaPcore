package com.yapcore.games.cmd;

import com.yapcore.games.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final MatchManager matches;

    public DuelCommand(MatchManager matches) {
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
            player.sendMessage("Usage: /duel <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }
        if (matches.acceptDuel(player.getUniqueId(), target.getUniqueId())) {
            return true;
        }
        if (!matches.challengeDuel(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage("§cCould not send duel (busy or invalid target).");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
