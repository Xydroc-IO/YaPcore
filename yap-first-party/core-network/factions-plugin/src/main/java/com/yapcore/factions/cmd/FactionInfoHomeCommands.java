package com.yapcore.factions.cmd;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.map.FactionMapRenderer;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class FactionInfoHomeCommands {

    private final FactionCommandSupport ctx;

    FactionInfoHomeCommands(FactionCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean desc(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f desc <text>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.factions.setDescription(member.get().factionId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aDescription updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean motd(Player player, String[] args) {
        if (args.length < 2) {
            var faction = ctx.factions.findByPlayer(player.getUniqueId());
            if (faction.isEmpty()) {
                player.sendMessage("§cYou are not in a faction.");
                return true;
            }
            player.sendMessage("§6MOTD: §f" + (faction.get().motd().isBlank() ? "(none)" : faction.get().motd()));
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.factions.setMotd(member.get().factionId(), text, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aMOTD updated.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean joinMode(Player player, FactionJoinMode mode) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        ctx.factions.setJoinMode(member.get().factionId(), mode, player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aJoin mode set to §f" + mode.name().toLowerCase(Locale.ROOT) + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean home(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
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
        YapSched.entity(ctx.plugin, player, () ->
                player.teleport(new org.bukkit.Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch())));
        return true;
    }

    boolean setHome(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        ctx.factions.setHome(member.get().factionId(), player.getLocation(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aFaction home set.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean delHome(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        ctx.factions.clearHome(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aFaction home removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean chat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.FACTION);
            player.sendMessage("§aFaction chat enabled. Use §f/f chat off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.PUBLIC);
            player.sendMessage("§7Faction chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.factions.sendFactionChat(player, message);
        return true;
    }

    boolean allyChat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.ALLY);
            player.sendMessage("§aAlly chat enabled. Use §f/f ac off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.PUBLIC);
            player.sendMessage("§7Ally chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.factions.sendAllyChat(player, message);
        return true;
    }

    boolean info(Player player, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = ctx.resolveFaction(args[1]).orElse(null);
        } else {
            faction = ctx.factions.findByPlayer(player.getUniqueId()).orElse(null);
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
        if (ctx.config.bankEnabled()) {
            player.sendMessage("§7Bank §f" + String.format("%.2f", faction.bankBalance()));
        }
        if (faction.home().isSet()) {
            player.sendMessage("§7Home §f" + faction.home().world());
        }
        return true;
    }

    boolean list(Player player) {
        var all = ctx.factions.listFactions();
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

    boolean members(Player player, String[] args) {
        Faction faction;
        if (args.length >= 2) {
            faction = ctx.resolveFaction(args[1]).orElse(null);
        } else {
            faction = ctx.factions.findByPlayer(player.getUniqueId()).orElse(null);
        }
        if (faction == null) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        List<FactionMember> members = ctx.factions.listMembers(faction.id());
        player.sendMessage("§6Members of §f" + faction.name() + " §7(" + members.size() + ")");
        for (FactionMember m : members) {
            String name = Bukkit.getOfflinePlayer(m.playerId()).getName();
            player.sendMessage("§f" + name + " §7- §8" + m.role().name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    boolean claims(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        List<FactionClaimOverlay> claims = ctx.factions.listClaims(faction.get().id());
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

    boolean top(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§eUsage: /f top [page]");
                return true;
            }
        }
        List<Faction> top = ctx.factions.topFactions(page, 10);
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

    boolean map(Player player) {
        for (String line : FactionMapRenderer.render(player, ctx.factions, ctx.config)) {
            player.sendMessage(line);
        }
        return true;
    }

    boolean power(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Faction f = faction.get();
        player.sendMessage("§7Faction power: §f" + f.power() + "§7/§f" + f.maxPower());
        return true;
    }

    boolean relation(Player player, String[] args, FactionRelation relation) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f " + relation.name().toLowerCase(Locale.ROOT) + " <faction>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        var other = ctx.resolveFaction(args[1]);
        if (other.isEmpty()) {
            player.sendMessage("§cFaction not found.");
            return true;
        }
        ctx.factions.setRelation(member.get().factionId(), other.get().id(), relation, player.getUniqueId())
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aRelation set: §f" + relation.name().toLowerCase(Locale.ROOT)
                                + " §7with §f" + other.get().name())))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }
}
