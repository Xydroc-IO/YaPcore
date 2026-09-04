package com.yapcore.factions.cmd;

import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FactionCommands implements CommandExecutor, TabCompleter {

    private final FactionCommandSupport ctx;
    private final FactionMembershipCommands membership;
    private final FactionInfoHomeCommands infoHome;
    private final FactionClaimBankCommands claimBank;

    public FactionCommands(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.ctx = new FactionCommandSupport(plugin, config, factions);
        this.membership = new FactionMembershipCommands(ctx);
        this.infoHome = new FactionInfoHomeCommands(ctx);
        this.claimBank = new FactionClaimBankCommands(ctx);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!player.hasPermission("yapfactions.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            ctx.sendHelp(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                ctx.sendHelp(player);
                yield true;
            }
            case "create" -> membership.create(player, args);
            case "disband" -> membership.disband(player);
            case "join" -> membership.join(player, args);
            case "leave" -> membership.leave(player);
            case "kick" -> membership.kick(player, args);
            case "invite" -> membership.invite(player, args);
            case "accept" -> membership.accept(player, args);
            case "deny" -> membership.deny(player, args);
            case "promote" -> membership.promote(player, args);
            case "demote" -> membership.demote(player, args);
            case "leader" -> membership.leader(player, args);
            case "desc", "description" -> infoHome.desc(player, args);
            case "motd" -> infoHome.motd(player, args);
            case "open" -> infoHome.joinMode(player, FactionJoinMode.OPEN);
            case "closed" -> infoHome.joinMode(player, FactionJoinMode.CLOSED);
            case "inviteonly" -> infoHome.joinMode(player, FactionJoinMode.INVITE);
            case "home" -> infoHome.home(player);
            case "sethome" -> infoHome.setHome(player);
            case "delhome" -> infoHome.delHome(player);
            case "chat", "c" -> infoHome.chat(player, args);
            case "allychat", "ac" -> infoHome.allyChat(player, args);
            case "info" -> infoHome.info(player, args);
            case "list" -> infoHome.list(player);
            case "members" -> infoHome.members(player, args);
            case "claims" -> infoHome.claims(player);
            case "top" -> infoHome.top(player, args);
            case "map" -> infoHome.map(player);
            case "power" -> infoHome.power(player);
            case "ally" -> infoHome.relation(player, args, FactionRelation.ALLY);
            case "enemy" -> infoHome.relation(player, args, FactionRelation.ENEMY);
            case "neutral" -> infoHome.relation(player, args, FactionRelation.NEUTRAL);
            case "claim" -> claimBank.claim(player);
            case "claimall" -> claimBank.claimAll(player);
            case "unclaim" -> claimBank.unclaim(player);
            case "deposit" -> claimBank.deposit(player, args);
            case "withdraw" -> claimBank.withdraw(player, args);
            case "balance", "bank" -> claimBank.bank(player);
            default -> {
                player.sendMessage("§cUnknown subcommand. Try §f/f help");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "create", "disband", "join", "leave", "kick", "invite", "accept",
                    "deny", "promote", "demote", "leader", "desc", "motd", "open", "closed", "inviteonly",
                    "home", "sethome", "delhome", "chat", "allychat", "info", "list", "members", "claims",
                    "top", "map", "power", "ally", "enemy", "neutral", "claim", "claimall", "unclaim",
                    "deposit", "withdraw", "bank")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        }
        return out;
    }
}
