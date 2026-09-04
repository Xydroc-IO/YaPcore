package com.yapcore.guilds.cmd;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildXpCalculator;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class GuildInfoHomeCommands {

    private final GuildCommandSupport ctx;

    GuildInfoHomeCommands(GuildCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean desc(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g desc <text>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.guilds.setDescription(member.get().guildId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aDescription updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean motd(Player player, String[] args) {
        if (args.length < 2) {
            var guild = ctx.guilds.findByPlayer(player.getUniqueId());
            if (guild.isEmpty()) {
                player.sendMessage("§cYou are not in a guild.");
                return true;
            }
            player.sendMessage("§6MOTD: §f" + (guild.get().motd().isBlank() ? "(none)" : guild.get().motd()));
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.guilds.setMotd(member.get().guildId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aMOTD updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean joinMode(Player player, GuildJoinMode mode) {
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        ctx.guilds.setJoinMode(member.get().guildId(), mode, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoin mode set to §f" + mode.name().toLowerCase(Locale.ROOT) + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean home(Player player) {
        var guild = ctx.guilds.findByPlayer(player.getUniqueId());
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
        YapSched.entity(ctx.plugin, player, () ->
                player.teleport(new org.bukkit.Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch())));
        return true;
    }

    boolean setHome(Player player) {
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        ctx.guilds.setHome(member.get().guildId(), player.getLocation(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aGuild home set.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean delHome(Player player) {
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        ctx.guilds.clearHome(member.get().guildId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aGuild home removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean chat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.GUILD);
            player.sendMessage("§aGuild chat enabled. Use §f/g chat off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Guild chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.guilds.sendGuildChat(player, message);
        return true;
    }

    boolean allyChat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.ALLY);
            player.sendMessage("§aAlly chat enabled. Use §f/g ac off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Ally chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.guilds.sendAllyChat(player, message);
        return true;
    }

    boolean info(Player player, String[] args) {
        Guild guild;
        if (args.length >= 2) {
            guild = ctx.resolveGuild(args[1]).orElse(null);
        } else {
            guild = ctx.guilds.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (guild == null) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        player.sendMessage("§d--- §f" + guild.name() + " §7[" + guild.tag() + "] §d---");
        player.sendMessage("§7Level §f" + guild.level() + " §7XP §f" + guild.xp());
        player.sendMessage("§7Members §f" + ctx.guilds.listMembers(guild.id()).size()
                + "§7/§f" + ctx.guilds.maxMembers(guild.id()));
        player.sendMessage("§7Leader §f" + Bukkit.getOfflinePlayer(guild.leaderId()).getName());
        player.sendMessage("§7Join §f" + guild.joinMode().name().toLowerCase(Locale.ROOT));
        if (!guild.description().isBlank()) {
            player.sendMessage("§7Desc §f" + guild.description());
        }
        if (!guild.motd().isBlank()) {
            player.sendMessage("§7MOTD §f" + guild.motd());
        }
        if (ctx.config.bankEnabled()) {
            player.sendMessage("§7Bank §f" + String.format("%.2f", guild.bankBalance())
                    + "§7/§f" + String.format("%.0f", ctx.guilds.bankCap(guild.id())));
        }
        if (guild.home().isSet()) {
            player.sendMessage("§7Home §f" + guild.home().world());
        }
        return true;
    }

    boolean list(Player player) {
        var all = ctx.guilds.listGuilds();
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

    boolean members(Player player, String[] args) {
        Guild guild;
        if (args.length >= 2) {
            guild = ctx.resolveGuild(args[1]).orElse(null);
        } else {
            guild = ctx.guilds.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (guild == null) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        List<GuildMember> members = ctx.guilds.listMembers(guild.id());
        player.sendMessage("§6Members of §f" + guild.name() + " §7(" + members.size() + ")");
        for (GuildMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §8" + m.role().name().toLowerCase(Locale.ROOT)
                    + " §7(§f" + m.contributionXp() + " xp§7)");
        }
        return true;
    }

    boolean top(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§eUsage: /g top [page]");
                return true;
            }
        }
        List<Guild> top = ctx.guilds.topGuilds(page, 10);
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

    boolean level(Player player) {
        var guild = ctx.guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Guild g = guild.get();
        long next = GuildXpCalculator.xpToAdvance(ctx.config.xpConfig(), g.level() + 1);
        player.sendMessage("§7Guild level §f" + g.level() + " §7XP §f" + g.xp()
                + (next > 0 ? "§7/§f" + next : " §7(max)"));
        player.sendMessage("§7Members §f" + ctx.guilds.listMembers(g.id()).size()
                + "§7/§f" + ctx.guilds.maxMembers(g.id()));
        return true;
    }

    boolean perks(Player player) {
        var guild = ctx.guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        Guild g = guild.get();
        player.sendMessage("§6Guild perks §7(level " + g.level() + ")");
        for (var entry : ctx.config.perkDescriptions().entrySet()) {
            String status = g.level() >= entry.getKey() ? "§a✓" : "§8○";
            player.sendMessage(status + " §7Lv " + entry.getKey() + ": §f" + entry.getValue());
        }
        return true;
    }

    boolean contrib(Player player) {
        var guild = ctx.guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        List<GuildMember> members = ctx.guilds.listMembers(guild.get().id());
        player.sendMessage("§6Contributions §7(" + guild.get().name() + ")");
        for (GuildMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §f" + m.contributionXp() + " xp");
        }
        return true;
    }

    boolean officerChat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.OFFICER);
            player.sendMessage("§aOfficer chat enabled. Use §f/g oc off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.guilds.chatState().setChannel(player.getUniqueId(), GuildChatState.Channel.PUBLIC);
            player.sendMessage("§7Officer chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.guilds.sendOfficerChat(player, message);
        return true;
    }

    boolean relation(Player player, String[] args, GuildRelation relation) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g " + relation.name().toLowerCase(Locale.ROOT) + " <guild>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        var other = ctx.resolveGuild(args[1]);
        if (other.isEmpty()) {
            player.sendMessage("§cGuild not found.");
            return true;
        }
        ctx.guilds.setRelation(member.get().guildId(), other.get().id(), relation, player.getUniqueId())
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aRelation set: §f" + relation.name().toLowerCase(Locale.ROOT)
                                + " §7with §f" + other.get().name())))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

}
