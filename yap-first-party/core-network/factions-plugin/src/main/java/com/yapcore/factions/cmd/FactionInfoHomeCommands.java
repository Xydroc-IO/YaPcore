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
            ctx.usage(player, "desc <text>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
                ctx.notInOrg(player);
                return true;
            }
            player.sendMessage("§6MOTD: §f" + (faction.get().motd().isBlank() ? "(none)" : faction.get().motd()));
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
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
            ctx.notInOrg(player);
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
            ctx.notInOrg(player);
            return true;
        }
        if (!faction.get().home().isSet()) {
            player.sendMessage("§cYour " + ctx.singularLower() + " has no home set.");
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
            ctx.notInOrg(player);
            return true;
        }
        ctx.factions.setHome(member.get().factionId(), player.getLocation(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§a" + ctx.singular() + " home set.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean delHome(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        ctx.factions.clearHome(member.get().factionId(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§a" + ctx.singular() + " home removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean chat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.FACTION);
            player.sendMessage("§a" + ctx.singular() + " chat enabled. Use §f/" + ctx.cmd() + " chat off §ato disable.");
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.PUBLIC);
            player.sendMessage("§7" + ctx.singular() + " chat disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        ctx.factions.sendFactionChat(player, message);
        return true;
    }

    boolean allyChat(Player player, String[] args) {
        if (args.length < 2) {
            ctx.factions.chatState().setChannel(player.getUniqueId(), FactionChatState.Channel.ALLY);
            player.sendMessage("§aAlly chat enabled. Use §f/" + ctx.cmd() + " ac off §ato disable.");
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
            player.sendMessage("§c" + ctx.singular() + " not found.");
            return true;
        }
        int members = ctx.factions.listMembers(faction.id()).size();
        int claims = ctx.factions.listClaims(faction.id()).size();
        player.sendMessage("§6--- §f" + faction.name() + " §7[" + faction.tag() + "] §6---");
        player.sendMessage("§7Power §f" + faction.power() + "§7/§f" + faction.maxPower()
                + " §8(available/max)"
                + (faction.isShielded() ? " §c[SHIELD]" : ""));
        if (faction.isShielded() && faction.shieldUntil() != null) {
            long secs = Math.max(0, faction.shieldUntil().getEpochSecond() - java.time.Instant.now().getEpochSecond());
            player.sendMessage("§7Shield §f" + (secs / 60) + "m " + (secs % 60) + "s remaining");
        }
        player.sendMessage("§7Leader §f" + Bukkit.getOfflinePlayer(faction.leaderId()).getName()
                + " §7· Members §f" + members + " §7· Land §f" + claims);
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
        var relations = ctx.factions.relationsFor(faction.id());
        if (!relations.isEmpty()) {
            StringBuilder allies = new StringBuilder();
            StringBuilder enemies = new StringBuilder();
            for (var e : relations) {
                Faction other = ctx.factions.getFaction(e.getKey()).orElse(null);
                String tag = other == null ? "#" + e.getKey() : other.tag();
                if (e.getValue() == FactionRelation.ALLY) {
                    if (!allies.isEmpty()) {
                        allies.append("§7, ");
                    }
                    allies.append("§b").append(tag);
                } else if (e.getValue() == FactionRelation.ENEMY) {
                    if (!enemies.isEmpty()) {
                        enemies.append("§7, ");
                    }
                    enemies.append("§c").append(tag);
                }
            }
            if (!allies.isEmpty()) {
                player.sendMessage("§7Allies " + allies);
            }
            if (!enemies.isEmpty()) {
                player.sendMessage("§7Enemies " + enemies);
            }
        }
        return true;
    }

    boolean list(Player player) {
        var all = ctx.factions.listFactions();
        if (all.isEmpty()) {
            player.sendMessage("§7No " + ctx.pluralLower() + " yet.");
            return true;
        }
        player.sendMessage("§6" + ctx.plural() + " §7(" + all.size() + ")");
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
            ctx.notFound(player);
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
            ctx.notInOrg(player);
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
                player.sendMessage("§eUsage: /" + ctx.cmd() + " top [page]");
                return true;
            }
        }
        List<Faction> top = ctx.factions.topFactions(page, 10);
        if (top.isEmpty()) {
            player.sendMessage("§7No " + ctx.plural().toLowerCase(Locale.ROOT) + " yet.");
            return true;
        }
        player.sendMessage("§6Top " + ctx.plural().toLowerCase(Locale.ROOT) + " §7(page " + page + ")");
        player.sendMessage("§8#  name              power     members land");
        int rank = (page - 1) * 10 + 1;
        for (Faction f : top) {
            int members = ctx.factions.listMembers(f.id()).size();
            int land = ctx.factions.listClaims(f.id()).size();
            player.sendMessage(String.format(
                    "§7%2d. §f%-16s §8%4d/%-4d §f%3d §8%4d",
                    rank, f.name(), f.power(), f.maxPower(), members, land));
            rank++;
        }
        return true;
    }

    boolean setWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /" + ctx.cmd() + " setwarp <name>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        ctx.factions.setWarp(member.get().factionId(), player.getUniqueId(), args[1], player.getLocation())
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aWarp §f" + args[1].toLowerCase(Locale.ROOT) + " §aset.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean delWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /" + ctx.cmd() + " delwarp <name>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        ctx.factions.deleteWarp(member.get().factionId(), player.getUniqueId(), args[1])
                .thenRun(() -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aWarp removed.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean warp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUsage: /" + ctx.cmd() + " warp <name>");
            return true;
        }
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        var warp = ctx.factions.warp(faction.get().id(), args[1]);
        if (warp.isEmpty()) {
            player.sendMessage("§cWarp not found.");
            return true;
        }
        var world = Bukkit.getWorld(warp.get().world());
        if (world == null) {
            player.sendMessage("§cWarp world unavailable.");
            return true;
        }
        var w = warp.get();
        YapSched.entity(ctx.plugin, player, () ->
                player.teleport(new org.bukkit.Location(world, w.x(), w.y(), w.z(), w.yaw(), w.pitch())));
        return true;
    }

    boolean warps(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        var list = ctx.factions.listWarps(faction.get().id());
        if (list.isEmpty()) {
            player.sendMessage("§7No warps set.");
            return true;
        }
        player.sendMessage("§6Warps §7(" + list.size() + ")");
        for (var w : list) {
            player.sendMessage("§f" + w.name() + " §7@ §f" + w.world());
        }
        return true;
    }

    boolean upkeep(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        if (!ctx.config.upkeepEnabled()) {
            player.sendMessage("§7Upkeep is disabled.");
            return true;
        }
        int claims = ctx.factions.listClaims(faction.get().id()).size();
        double due = claims * ctx.config.upkeepCostPerClaim();
        player.sendMessage("§6Upkeep §7every period · §f" + claims + " §7linked claims");
        player.sendMessage("§7Cost §f" + String.format("%.2f", due)
                + " §7· Bank §f" + String.format("%.2f", faction.get().bankBalance()));
        if (faction.get().upkeepUnpaidSince() != null) {
            long graceH = ctx.config.upkeepGraceHours();
            long elapsedH = java.time.Duration.between(
                    faction.get().upkeepUnpaidSince(), java.time.Instant.now()).toHours();
            player.sendMessage("§cUnpaid since §f" + faction.get().upkeepUnpaidSince()
                    + " §7· grace §f" + graceH + "h §7· elapsed §f" + elapsedH + "h");
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
            ctx.notInOrg(player);
            return true;
        }
        Faction f = faction.get();
        player.sendMessage("§7" + ctx.singular() + " power: §f" + f.power() + "§7/§f" + f.maxPower());
        return true;
    }

    boolean relation(Player player, String[] args, FactionRelation relation) {
        if (args.length < 2) {
            ctx.usage(player, relation.name().toLowerCase(Locale.ROOT) + " <" + ctx.singularLower() + ">");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
        if (member.isEmpty()) {
            ctx.notInOrg(player);
            return true;
        }
        var other = ctx.resolveFaction(args[1]);
        if (other.isEmpty()) {
            ctx.notFound(player);
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
