package com.yapcore.factions.cmd;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.integration.ClaimIntegration;
import com.yapcore.factions.integration.EconomyIntegration;
import com.yapcore.factions.map.FactionMapRenderer;
import com.yapcore.factions.service.FactionServiceImpl;
import com.yapcore.playerdata.claims.Claim;
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

public final class FactionCommands implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private final FactionServiceImpl factions;

    public FactionCommands(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.config = config;
        this.factions = factions;
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
            case "open" -> handleJoinMode(player, FactionJoinMode.OPEN);
            case "closed" -> handleJoinMode(player, FactionJoinMode.CLOSED);
            case "inviteonly" -> handleJoinMode(player, FactionJoinMode.INVITE);
            case "home" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "delhome" -> handleDelHome(player);
            case "chat", "c" -> handleChat(player, args);
            case "allychat", "ac" -> handleAllyChat(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "members" -> handleMembers(player, args);
            case "claims" -> handleClaims(player);
            case "top" -> handleTop(player, args);
            case "map" -> handleMap(player);
            case "power" -> handlePower(player);
            case "ally" -> handleRelation(player, args, FactionRelation.ALLY);
            case "enemy" -> handleRelation(player, args, FactionRelation.ENEMY);
            case "neutral" -> handleRelation(player, args, FactionRelation.NEUTRAL);
            case "claim" -> handleClaim(player);
            case "claimall" -> handleClaimAll(player);
            case "unclaim" -> handleUnclaim(player);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "balance", "bank" -> handleBank(player);
            default -> {
                player.sendMessage("§cUnknown subcommand. Try §f/f help");
                yield true;
            }
        };
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!player.hasPermission("yapfactions.create")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§eUsage: /f create <name> <tag>");
            return true;
        }
        factions.create(args[1], args[2], player.getUniqueId()).thenAccept(f ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aCreated faction §f" + f.name() + " §7[" + f.tag() + "]")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDisband(Player player) {
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        factions.disband(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aFaction disbanded.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f join <faction>");
            return true;
        }
        var target = resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        factions.join(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleLeave(Player player) {
        factions.leave(player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aLeft your faction.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f kick <player>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        factions.kick(member.get().factionId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aKicked §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f invite <player>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        factions.invite(member.get().factionId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
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
            player.sendMessage("§eUsage: /f accept <faction>");
            return true;
        }
        var target = resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        factions.acceptInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
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
            player.sendMessage("§eUsage: /f deny <faction>");
            return true;
        }
        var target = resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        factions.denyInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
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
            player.sendMessage("§eUsage: /f " + (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        var action = promote
                ? factions.promote(member.get().factionId(), target.getUniqueId(), player.getUniqueId())
                : factions.demote(member.get().factionId(), target.getUniqueId(), player.getUniqueId());
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
            player.sendMessage("§eUsage: /f leader <player>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        factions.transferLeadership(member.get().factionId(), target.getUniqueId(), player.getUniqueId())
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
            player.sendMessage("§eUsage: /f desc <text>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        factions.setDescription(member.get().factionId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aDescription updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleMotd(Player player, String[] args) {
        if (args.length < 2) {
            var faction = factions.findByPlayer(player.getUniqueId());
            if (faction.isEmpty()) {
                player.sendMessage("§cYou are not in a faction.");
                return true;
            }
            player.sendMessage("§6MOTD: §f" + (faction.get().motd().isBlank() ? "(none)" : faction.get().motd()));
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        factions.setMotd(member.get().factionId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aMOTD updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleJoinMode(Player player, FactionJoinMode mode) {
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        factions.setJoinMode(member.get().factionId(), mode, player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aJoin mode set to §f" + mode.name().toLowerCase(Locale.ROOT) + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleHome(Player player) {
        var faction = factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        if (!faction.get().home().isSet()) {
            player.sendMessage("§cYour faction has no home set.");
            return true;
        }
        var home = faction.get().home();
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
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        factions.setHome(member.get().factionId(), player.getLocation(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aFaction home set.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDelHome(Player player) {
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        factions.clearHome(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aFaction home removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleChat(Player player, String[] args) {
        if (args.length < 2) {
            factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.FACTION);
            player.sendMessage("§aFaction chat enabled. Use §f/f chat off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.PUBLIC);
            player.sendMessage("§7Faction chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        factions.sendFactionChat(player, message);
        return true;
    }

    private boolean handleAllyChat(Player player, String[] args) {
        if (args.length < 2) {
            factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.ALLY);
            player.sendMessage("§aAlly chat enabled. Use §f/f ac off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.PUBLIC);
            player.sendMessage("§7Ally chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        factions.sendAllyChat(player, message);
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = resolveFaction(args[1]).orElse(null);
        } else {
            faction = factions.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (faction == null) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        player.sendMessage("§6--- §f" + faction.name() + " §7[" + faction.tag() + "] §6---");
        player.sendMessage("§7Power §f" + faction.power() + "§7/§f" + faction.maxPower()
                + (faction.isShielded() ? " §c[SHIELD]" : ""));
        player.sendMessage("§7Leader §f" + Bukkit.getOfflinePlayer(faction.leaderId()).getName());
        player.sendMessage("§7Join §f" + faction.joinMode().name().toLowerCase(Locale.ROOT));
        if (!faction.description().isBlank()) {
            player.sendMessage("§7Desc §f" + faction.description());
        }
        if (!faction.motd().isBlank()) {
            player.sendMessage("§7MOTD §f" + faction.motd());
        }
        if (config.bankEnabled()) {
            player.sendMessage("§7Bank §f" + String.format("%.2f", faction.bankBalance()));
        }
        if (faction.home().isSet()) {
            player.sendMessage("§7Home §f" + faction.home().world());
        }
        return true;
    }

    private boolean handleList(Player player) {
        var all = factions.listFactions();
        if (all.isEmpty()) {
            player.sendMessage("§7No factions yet.");
            return true;
        }
        player.sendMessage("§6Factions §7(" + all.size() + ")");
        for (Faction f : all) {
            player.sendMessage("§f" + f.name() + " §7[" + f.tag() + "] §8power "
                    + f.power() + "/" + f.maxPower());
        }
        return true;
    }

    private boolean handleMembers(Player player, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = resolveFaction(args[1]).orElse(null);
        } else {
            faction = factions.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (faction == null) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        List<FactionMember> members = factions.listMembers(faction.id());
        player.sendMessage("§6Members of §f" + faction.name() + " §7(" + members.size() + ")");
        for (FactionMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §8" + m.role().name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private boolean handleClaims(Player player) {
        var faction = factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        List<FactionClaimOverlay> claims = factions.listClaims(faction.get().id());
        if (claims.isEmpty()) {
            player.sendMessage("§7No linked claims.");
            return true;
        }
        player.sendMessage("§6Linked claims §7(" + claims.size() + ")");
        for (FactionClaimOverlay overlay : claims) {
            player.sendMessage("§f#" + overlay.claimId() + " §7cost §f" + overlay.powerCost());
        }
        return true;
    }

    private boolean handleTop(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§eUsage: /f top [page]");
                return true;
            }
        }
        List<Faction> top = factions.topFactions(page, 10);
        if (top.isEmpty()) {
            player.sendMessage("§7No factions yet.");
            return true;
        }
        player.sendMessage("§6Top factions §7(page " + page + ")");
        int rank = (page - 1) * 10 + 1;
        for (Faction f : top) {
            player.sendMessage("§7" + rank + ". §f" + f.name() + " §8" + f.power() + "/" + f.maxPower());
            rank++;
        }
        return true;
    }

    private boolean handleMap(Player player) {
        for (String line : FactionMapRenderer.render(player, factions, config)) {
            player.sendMessage(line);
        }
        return true;
    }

    private boolean handlePower(Player player) {
        var faction = factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Faction f = faction.get();
        player.sendMessage("§7Faction power: §f" + f.power() + "§7/§f" + f.maxPower());
        return true;
    }

    private boolean handleRelation(Player player, String[] args, FactionRelation relation) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f " + relation.name().toLowerCase(Locale.ROOT) + " <faction>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        var other = resolveFaction(args[1]);
        if (other.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        factions.setRelation(member.get().factionId(), other.get().id(), relation, player.getUniqueId())
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aRelation set: §f" + relation.name().toLowerCase(Locale.ROOT)
                                + " §7with §f" + other.get().name())))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleClaim(Player player) {
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Claim claim = ClaimIntegration.claimAt(player).orElse(null);
        if (claim == null) {
            player.sendMessage("§cStand in a playerdata claim to link it.");
            return true;
        }
        if (!ClaimIntegration.canManageClaim(player, claim) && !ClaimIntegration.isAdmin(player)) {
            player.sendMessage("§cYou must own or manage this claim.");
            return true;
        }
        if (factions.overlayForClaim(claim.id()).isPresent()) {
            player.sendMessage("§cThis claim is already faction-linked.");
            return true;
        }
        long factionId = member.get().factionId();
        factions.linkClaim(claim.id(), factionId, player.getUniqueId(), claim.area())
                .thenAccept(overlay -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aClaim §f#" + claim.id() + " §alinked (power cost "
                                + overlay.powerCost() + ").")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleClaimAll(Player player) {
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        List<Claim> claims = ClaimIntegration.manageableClaims(player);
        if (claims.isEmpty()) {
            player.sendMessage("§cNo manageable claims.");
            return true;
        }
        List<Long> ids = new ArrayList<>();
        List<Integer> areas = new ArrayList<>();
        for (Claim claim : claims) {
            if (factions.overlayForClaim(claim.id()).isEmpty()) {
                ids.add(claim.id());
                areas.add(claim.area());
            }
        }
        if (ids.isEmpty()) {
            player.sendMessage("§cAll manageable claims are already linked.");
            return true;
        }
        factions.linkAllClaims(member.get().factionId(), player.getUniqueId(), ids, areas)
                .thenAccept(count -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aLinked §f" + count + " §aclaim(s).")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleUnclaim(Player player) {
        Claim claim = ClaimIntegration.claimAt(player).orElse(null);
        if (claim == null) {
            player.sendMessage("§cStand in a linked claim.");
            return true;
        }
        factions.unlinkClaim(claim.id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aClaim §f#" + claim.id() + " §aunlinked from faction.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleDeposit(Player player, String[] args) {
        if (!config.bankEnabled()) {
            player.sendMessage("§cFaction bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f deposit <amount>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
        factions.bankDeposit(member.get().factionId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aDeposited §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleWithdraw(Player player, String[] args) {
        if (!config.bankEnabled()) {
            player.sendMessage("§cFaction bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f withdraw <amount>");
            return true;
        }
        var member = factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
        factions.bankWithdraw(member.get().factionId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(plugin, player, () -> player.sendMessage("§aWithdrew §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean handleBank(Player player) {
        var faction = factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        player.sendMessage("§7Faction bank: §f" + String.format("%.2f", faction.get().bankBalance()));
        player.sendMessage("§7Your balance: §f" + String.format("%.2f", EconomyIntegration.balance(player)));
        return true;
    }

    private java.util.Optional<Faction> resolveFaction(String raw) {
        return factions.findByName(raw).or(() -> factions.findByTag(raw));
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6--- YaP Factions ---");
        player.sendMessage("§6/f create|disband|join|leave|kick|invite|accept|deny");
        player.sendMessage("§6/f promote|demote|leader|desc|motd|open|closed|inviteonly");
        player.sendMessage("§6/f home|sethome|delhome|chat|allychat|members|claims|top|map");
        player.sendMessage("§6/f ally|enemy|neutral|claim|claimall|unclaim|deposit|withdraw|bank");
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
