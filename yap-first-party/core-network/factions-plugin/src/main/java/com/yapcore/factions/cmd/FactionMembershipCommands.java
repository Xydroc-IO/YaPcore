package com.yapcore.factions.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class FactionMembershipCommands {

    private final FactionCommandSupport ctx;

    FactionMembershipCommands(FactionCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean create(Player player, String[] args) {
        if (!player.hasPermission("yapfactions.create")) {
            YapMessages.noPermission(player, "yapfactions.create");
            return true;
        }
        if (args.length < 3) {
            ctx.usage(player, "create <name> <tag>");
            return true;
        }
        ctx.factions.create(args[1], args[2], player.getUniqueId()).thenAccept(f ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aCreated " + ctx.singularLower() + " §f" + f.name()
                                + " §7[" + f.tag() + "]")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean disband(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        ctx.factions.disband(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§a" + ctx.singular() + " disbanded.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean join(Player player, String[] args) {
        if (args.length < 2) {
            ctx.usage(player, "join <" + ctx.singularLower() + ">");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            ctx.notFound(player);
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
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aLeft your " + ctx.singularLower() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean kick(Player player, String[] args) {
        if (args.length < 2) {
            ctx.usage(player, "kick <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
            ctx.usage(player, "invite <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
            ctx.usage(player, "accept <" + ctx.singularLower() + ">");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            ctx.notFound(player);
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
            ctx.usage(player, "deny <" + ctx.singularLower() + ">");
            return true;
        }
        var target = ctx.resolveFaction(args[1]);
        if (target.isEmpty()) {
            ctx.notFound(player);
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
            ctx.usage(player, (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
            ctx.usage(player, "leader <player>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
