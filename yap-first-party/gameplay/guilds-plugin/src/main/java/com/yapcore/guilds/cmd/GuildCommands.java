package com.yapcore.guilds.cmd;

import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.service.GuildServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuildCommands implements CommandExecutor, TabCompleter {

    private final GuildCommandSupport ctx;
    private final GuildMembershipCommands membership;
    private final GuildInfoHomeCommands infoHome;
    private final GuildBankCommands bank;

    public GuildCommands(JavaPlugin plugin, GuildsConfig config, GuildServiceImpl guilds) {
        this.ctx = new GuildCommandSupport(plugin, config, guilds);
        this.membership = new GuildMembershipCommands(ctx);
        this.infoHome = new GuildInfoHomeCommands(ctx);
        this.bank = new GuildBankCommands(ctx);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!player.hasPermission("yapguilds.use")) {
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
            case "open" -> infoHome.joinMode(player, GuildJoinMode.OPEN);
            case "closed" -> infoHome.joinMode(player, GuildJoinMode.CLOSED);
            case "inviteonly" -> infoHome.joinMode(player, GuildJoinMode.INVITE);
            case "home" -> infoHome.home(player);
            case "sethome" -> infoHome.setHome(player);
            case "delhome" -> infoHome.delHome(player);
            case "chat", "c" -> infoHome.chat(player, args);
            case "allychat", "ac" -> infoHome.allyChat(player, args);
            case "info" -> infoHome.info(player, args);
            case "list" -> infoHome.list(player);
            case "members" -> infoHome.members(player, args);
            case "top" -> infoHome.top(player, args);
            case "level" -> infoHome.level(player);
            case "perks" -> infoHome.perks(player);
            case "contrib", "contribution" -> infoHome.contrib(player);
            case "oc" -> infoHome.officerChat(player, args);
            case "ally" -> infoHome.relation(player, args, GuildRelation.ALLY);
            case "enemy" -> infoHome.relation(player, args, GuildRelation.ENEMY);
            case "neutral" -> infoHome.relation(player, args, GuildRelation.NEUTRAL);
            case "deposit" -> bank.deposit(player, args);
            case "withdraw" -> bank.withdraw(player, args);
            case "balance", "bank" -> bank.bank(player);
            default -> {
                player.sendMessage("§cUnknown subcommand. Try §f/g help");
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
                    "home", "sethome", "delhome", "chat", "oc", "ac", "info", "list", "members",
                    "top", "level", "perks", "contrib", "ally", "enemy", "neutral",
                    "deposit", "withdraw", "bank")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        }
        return out;
    }
}
