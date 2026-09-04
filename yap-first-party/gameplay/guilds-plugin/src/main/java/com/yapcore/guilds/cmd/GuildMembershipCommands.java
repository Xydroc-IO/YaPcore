package com.yapcore.guilds.cmd;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class GuildMembershipCommands {

    private final GuildCommandSupport ctx;

    GuildMembershipCommands(GuildCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean create(Player player, String[] args) {
        if (!player.hasPermission("yapctx.guilds.create")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§eUsage: /g create <name> <tag>");
            return true;
        }
        ctx.guilds.create(args[1], args[2], player.getUniqueId()).thenAccept(f ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aCreated guild §f" + f.name() + " §7[" + f.tag() + "]")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean disband(Player player) {
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        ctx.guilds.disband(member.get().guildId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aGuild disbanded.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean join(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g join <guild>");
            return true;
        }
        var target = ctx.resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        ctx.guilds.join(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean leave(Player player) {
        ctx.guilds.leave(player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aLeft your guild.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean kick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g kick <player>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.guilds.kick(member.get().guildId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aKicked §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean invite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g invite <player>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.guilds.invite(member.get().guildId(), target.getUniqueId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aInvited §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean accept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g accept <guild>");
            return true;
        }
        var target = ctx.resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        ctx.guilds.acceptInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoined §f" + target.get().name() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean deny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g deny <guild>");
            return true;
        }
        var target = ctx.resolveGuild(args[1]);
        if (target.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        ctx.guilds.denyInvite(target.get().id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aInvite declined.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
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
            player.sendMessage("§eUsage: /g " + (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
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
                ? ctx.guilds.promote(member.get().guildId(), target.getUniqueId(), player.getUniqueId())
                : ctx.guilds.demote(member.get().guildId(), target.getUniqueId(), player.getUniqueId());
        action.thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aUpdated rank for §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean leader(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g leader <player>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.guilds.transferLeadership(member.get().guildId(), target.getUniqueId(), player.getUniqueId())
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aLeadership transferred to §f" + target.getName() + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

}
