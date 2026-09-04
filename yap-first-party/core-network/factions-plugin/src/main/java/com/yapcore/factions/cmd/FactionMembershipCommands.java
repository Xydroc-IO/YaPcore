package com.yapcore.factions.cmd;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class FactionMembershipCommands {

    private final FactionCommandSupport ctx;

    FactionMembershipCommands(FactionCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean create(Player player, String[] args) {
        if (!player.hasPermission("yapfactions.create")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§eUsage: /f create <name> <tag>");
            return true;
        }
        ctx.factions.create(args[1], args[2], player.getUniqueId()).thenAccept(f ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aCreated faction §f" + f.name() + " §7[" + f.tag() + "]")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean disband(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        ctx.factions.disband(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aFaction disbanded.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean join(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f join <faction>");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        ctx.factions.join(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean leave(Player player) {
        ctx.factions.leave(player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aLeft your faction.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean kick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f kick <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.factions.kick(member.get().factionId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aKicked §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean invite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f invite <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.factions.invite(member.get().factionId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aInvited §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean accept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f accept <faction>");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        ctx.factions.acceptInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean deny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f deny <faction>");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        ctx.factions.denyInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aInvite declined.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean promote(Player player, String[] args) {
        return roleChange(player, args, true);
    }

    boolean demote(Player player, String[] args) {
        return roleChange(player, args, false);
    }

    private boolean roleChange(Player player, String[] args, boolean promote) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f " + (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
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
                ? ctx.factions.promote(member.get().factionId(), target.getUniqueId(), player.getUniqueId())
                : ctx.factions.demote(member.get().factionId(), target.getUniqueId(), player.getUniqueId());
        action.thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aUpdated rank for §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean leader(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f leader <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.factions.transferLeadership(member.get().factionId(), target.getUniqueId(), player.getUniqueId())
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aLeadership transferred to §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }
}
