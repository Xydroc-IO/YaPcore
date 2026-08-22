package com.yapcore.guilds.cmd;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRole;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.guilds.GuildXpCalculator;
import com.yapcore.guilds.integration.EconomyIntegration;
import com.yapcore.guilds.service.GuildServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
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

    private final JavaPlugin plugin;
    private final GuildsConfig config;
    private final GuildServiceImpl guilds;

    public GuildCommands(JavaPlugin plugin, GuildsConfig config, GuildServiceImpl guilds) {
        this.plugin = plugin;
        this.config = config;
        this.guilds = guilds;
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
            sendHelp(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(player);
                yield true;
            }
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny" -> handleDeny(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "leader" -> handleLeader(player, args);
            case "desc", "description" -> handleDesc(player, args);
            case "motd" -> handleMotd(player, args);
            case "open" -> handleJoinMode(player, GuildJoinMode.OPEN);
            case "closed" -> handleJoinMode(player, GuildJoinMode.CLOSED);
            case "inviteonly" -> handleJoinMode(player, GuildJoinMode.INVITE);
            case "home" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "delhome" -> handleDelHome(player);
            case "chat", "c" -> handleChat(player, args);
            case "allychat", "ac" -> handleAllyChat(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "members" -> handleMembers(player, args);
            case "top" -> handleTop(player, args);
            case "level" -> handleLevel(player);
            case "perks" -> handlePerks(player);
            case "contrib", "contribution" -> handleContrib(player);
            case "oc" -> handleOfficerChat(player, args);
            case "ally" -> handleRelation(player, args, GuildRelation.ALLY);
            case "enemy" -> handleRelation(player, args, GuildRelation.ENEMY);
            case "neutral" -> handleRelation(player, args, GuildRelation.NEUTRAL);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "balance", "bank" -> handleBank(player);
            default -> {
                player.sendMessage("§cUnknown subcommand. Try §f/g help");
                yield true;
            }
        };
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!player.hasPermission("yapguilds.create")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§eUsage: /g create <name> <tag>");
            return true;
        }
        guilds.create(args[1], args[2], player.getUniqueId()).thenAccept(f ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aCreated guild §f" + f.name() + " §7[" + f.tag() + "]")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDisband(Player player) {
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        guilds.disband(member.get().guildId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aGuild disbanded.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g join <guild>");
            return true;
        }
        var target = resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        guilds.join(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleLeave(Player player) {
        guilds.leave(player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aLeft your guild.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g kick <player>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        guilds.kick(member.get().guildId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aKicked §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g invite <player>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        guilds.invite(member.get().guildId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aInvited §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g accept <guild>");
            return true;
        }
        var target = resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        guilds.acceptInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDeny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g deny <guild>");
            return true;
        }
        var target = resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        guilds.denyInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aInvite declined.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handlePromote(Player player, String[] args) {
        return roleChange(player, args, true);
    }

    private boolean handleDemote(Player player, String[] args) {
        return roleChange(player, args, false);
    }

    private boolean roleChange(Player player, String[] args, boolean promote) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g " + (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        var action = promote
                ? guilds.promote(member.get().guildId(), target.getUniqueId(), player.getUniqueId())
                : guilds.demote(member.get().guildId(), target.getUniqueId(), player.getUniqueId());
        action.thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aUpdated rank for §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleLeader(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g leader <player>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        guilds.transferLeadership(member.get().guildId(), target.getUniqueId(), player.getUniqueId())
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aLeadership transferred to §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDesc(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g desc <text>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guilds.setDescription(member.get().guildId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aDescription updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleMotd(Player player, String[] args) {
        if (args.length < 2) {
            var guild = guilds.findByPlayer(player.getUniqueId());
            if (guild.isEmpty()) {
                player.sendMessage("§cYou are not in a guild.");
                return true;
            }
            player.sendMessage("§6MOTD: §f" + (guild.get().motd().isBlank() ? "(none)" : guild.get().motd()));
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guilds.setMotd(member.get().guildId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aMOTD updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleJoinMode(Player player, GuildJoinMode mode) {
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        guilds.setJoinMode(member.get().guildId(), mode, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aJoin mode set to §f" + mode.name().toLowerCase(Locale.ROOT) + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleHome(Player player) {
        var guild = guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        if (!guild.get().home().isSet()) {
            player.sendMessage("§cYour guild has no home set.");
            return true;
        }
        var home = guild.get().home();
        var world = Bukkit.getWorld(home.world());
        if (world == null) {
            player.sendMessage("§cHome world unavailable.");
            return true;
        }
        YapSched.entity(plugin, player, () ->
                player.teleport(new org.bukkit.Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch())));
        return true;
    }

    private boolean handleSetHome(Player player) {
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        guilds.setHome(member.get().guildId(), player.getLocation(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aGuild home set.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDelHome(Player player) {
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        guilds.clearHome(member.get().guildId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aGuild home removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleChat(Player player, String[] args) {
        if (args.length < 2) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.GUILD);
            player.sendMessage("§aGuild chat enabled. Use §f/g chat off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Guild chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guilds.sendGuildChat(player, message);
        return true;
    }

    private boolean handleAllyChat(Player player, String[] args) {
        if (args.length < 2) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.ALLY);
            player.sendMessage("§aAlly chat enabled. Use §f/g ac off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Ally chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guilds.sendAllyChat(player, message);
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Guild guild;
        if (args.length >= 2) {
            guild = resolveGuild(args[1]).orElse(null);
        } else {
            guild = guilds.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (guild == null) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        player.sendMessage("§d--- §f" + guild.name() + " §7[" + guild.tag() + "] §d---");
        player.sendMessage("§7Level §f" + guild.level() + " §7XP §f" + guild.xp());
        player.sendMessage("§7Members §f" + guilds.listMembers(guild.id()).size()
                + "§7/§f" + guilds.maxMembers(guild.id()));
        player.sendMessage("§7Leader §f" + Bukkit.getOfflinePlayer(guild.leaderId()).getName());
        player.sendMessage("§7Join §f" + guild.joinMode().name().toLowerCase(Locale.ROOT));
        if (!guild.description().isBlank()) {
            player.sendMessage("§7Desc §f" + guild.description());
        }
        if (!guild.motd().isBlank()) {
            player.sendMessage("§7MOTD §f" + guild.motd());
        }
        if (config.bankEnabled()) {
            player.sendMessage("§7Bank §f" + String.format("%.2f", guild.bankBalance())
                    + "§7/§f" + String.format("%.0f", guilds.bankCap(guild.id())));
        }
        if (guild.home().isSet()) {
            player.sendMessage("§7Home §f" + guild.home().world());
        }
        return true;
    }

    private boolean handleList(Player player) {
        var all = guilds.listGuilds();
        if (all.isEmpty()) {
            player.sendMessage("§7No guilds yet.");
            return true;
        }
        player.sendMessage("§6Guilds §7(" + all.size() + ")");
        for (Guild g : all) {
            player.sendMessage("§f" + g.name() + " §7[" + g.tag() + "] §8lvl "
                    + g.level() + " §7xp " + g.xp());
        }
        return true;
    }

    private boolean handleMembers(Player player, String[] args) {
        Guild guild;
        if (args.length >= 2) {
            guild = resolveGuild(args[1]).orElse(null);
        } else {
            guild = guilds.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (guild == null) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        List<GuildMember> members = guilds.listMembers(guild.id());
        player.sendMessage("§6Members of §f" + guild.name() + " §7(" + members.size() + ")");
        for (GuildMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §8" + m.role().name().toLowerCase(Locale.ROOT)
                    + " §7(§f" + m.contributionXp() + " xp§7)");
        }
        return true;
    }

    private boolean handleTop(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§eUsage: /g top [page]");
                return true;
            }
        }
        List<Guild> top = guilds.topGuilds(page, 10);
        if (top.isEmpty()) {
            player.sendMessage("§7No guilds yet.");
            return true;
        }
        player.sendMessage("§6Top guilds §7(page " + page + ")");
        int rank = (page - 1) * 10 + 1;
        for (Guild g : top) {
            player.sendMessage("§7" + rank + ". §f" + g.name() + " §8lvl " + g.level()
                    + " §7xp " + g.xp());
            rank++;
        }
        return true;
    }

    private boolean handleLevel(Player player) {
        var guild = guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Guild g = guild.get();
        long next = GuildXpCalculator.xpToAdvance(config.xpConfig(), g.level() + 1);
        player.sendMessage("§7Guild level §f" + g.level() + " §7XP §f" + g.xp()
                + (next > 0 ? "§7/§f" + next : " §7(max)"));
        player.sendMessage("§7Members §f" + guilds.listMembers(g.id()).size()
                + "§7/§f" + guilds.maxMembers(g.id()));
        return true;
    }

    private boolean handlePerks(Player player) {
        var guild = guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Guild g = guild.get();
        player.sendMessage("§6Guild perks §7(level " + g.level() + ")");
        for (var entry : config.perkDescriptions().entrySet()) {
            String status = g.level() >= entry.getKey() ? "§a✓" : "§8○";
            player.sendMessage(status + " §7Lv " + entry.getKey() + ": §f" + entry.getValue());
        }
        return true;
    }

    private boolean handleContrib(Player player) {
        var guild = guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        List<GuildMember> members = guilds.listMembers(guild.get().id());
        player.sendMessage("§6Contributions §7(" + guild.get().name() + ")");
        for (GuildMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §f" + m.contributionXp() + " xp");
        }
        return true;
    }

    private boolean handleOfficerChat(Player player, String[] args) {
        if (args.length < 2) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.OFFICER);
            player.sendMessage("§aOfficer chat enabled. Use §f/g oc off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Officer chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        guilds.sendOfficerChat(player, message);
        return true;
    }

    private boolean handleRelation(Player player, String[] args, GuildRelation relation) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g " + relation.name().toLowerCase(Locale.ROOT) + " <guild>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        var other = resolveGuild(args[1]);
        if (other.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        guilds.setRelation(member.get().guildId(), other.get().id(), relation, player.getUniqueId())
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aRelation set: §f" + relation.name().toLowerCase(Locale.ROOT)
                                + " §7with §f" + other.get().name())))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDeposit(Player player, String[] args) {
        if (!config.bankEnabled()) {
            player.sendMessage("§cGuild bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g deposit <amount>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
        guilds.bankDeposit(member.get().guildId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aDeposited §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleWithdraw(Player player, String[] args) {
        if (!config.bankEnabled()) {
            player.sendMessage("§cGuild bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g withdraw <amount>");
            return true;
        }
        var member = guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
        guilds.bankWithdraw(member.get().guildId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aWithdrew §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleBank(Player player) {
        var guild = guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        player.sendMessage("§7Guild bank: §f" + String.format("%.2f", guild.get().bankBalance())
                + "§7/§f" + String.format("%.0f", guilds.bankCap(guild.get().id())));
        player.sendMessage("§7Your balance: §f" + String.format("%.2f", EconomyIntegration.balance(player)));
        return true;
    }

    private java.util.Optional<Guild> resolveGuild(String raw) {
        return guilds.findByName(raw).or(() -> guilds.findByTag(raw));
    }

    private void sendHelp(Player player) {
        player.sendMessage("§d--- YaP Guilds ---");
        player.sendMessage("§6/g create|disband|join|leave|kick|invite|accept|deny");
        player.sendMessage("§6/g promote|demote|leader|desc|motd|open|closed|inviteonly");
        player.sendMessage("§6/g home|sethome|delhome|chat|oc|ac|members|top|level|perks|contrib");
        player.sendMessage("§6/g ally|enemy|neutral|deposit|withdraw|bank|info|list");
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "failed" : cur.getMessage();
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
