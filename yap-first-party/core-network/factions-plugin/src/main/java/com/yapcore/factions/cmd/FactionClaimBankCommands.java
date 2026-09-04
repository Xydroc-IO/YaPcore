package com.yapcore.factions.cmd;

import com.yapcore.factions.integration.ClaimIntegration;
import com.yapcore.factions.integration.EconomyIntegration;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

final class FactionClaimBankCommands {

    private final FactionCommandSupport ctx;

    FactionClaimBankCommands(FactionCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean claim(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
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
        if (ctx.factions.overlayForClaim(claim.id()).isPresent()) {
            player.sendMessage("§cThis claim is already faction-linked.");
            return true;
        }
        long factionId = member.get().factionId();
        ctx.factions.linkClaim(claim.id(), factionId, player.getUniqueId(), claim.area())
                .thenAccept(overlay -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aClaim §f#" + claim.id() + " §alinked (power cost "
                                + overlay.powerCost() + ").")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean claimAll(Player player) {
        var member = ctx.factions.member(player.getUniqueId());
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
            if (ctx.factions.overlayForClaim(claim.id()).isEmpty()) {
                ids.add(claim.id());
                areas.add(claim.area());
            }
        }
        if (ids.isEmpty()) {
            player.sendMessage("§cAll manageable claims are already linked.");
            return true;
        }
        ctx.factions.linkAllClaims(member.get().factionId(), player.getUniqueId(), ids, areas)
                .thenAccept(count -> YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aLinked §f" + count + " §aclaim(s).")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean unclaim(Player player) {
        Claim claim = ClaimIntegration.claimAt(player).orElse(null);
        if (claim == null) {
            player.sendMessage("§cStand in a linked claim.");
            return true;
        }
        ctx.factions.unlinkClaim(claim.id(), player.getUniqueId()).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () ->
                        player.sendMessage("§aClaim §f#" + claim.id() + " §aunlinked from faction.")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean deposit(Player player, String[] args) {
        if (!ctx.config.bankEnabled()) {
            player.sendMessage("§cFaction bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f deposit <amount>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
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
        ctx.factions.bankDeposit(member.get().factionId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aDeposited §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean withdraw(Player player, String[] args) {
        if (!ctx.config.bankEnabled()) {
            player.sendMessage("§cFaction bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /f withdraw <amount>");
            return true;
        }
        var member = ctx.factions.member(player.getUniqueId());
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
        ctx.factions.bankWithdraw(member.get().factionId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aWithdrew §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + FactionCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean bank(Player player) {
        var faction = ctx.factions.findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        player.sendMessage("§7Faction bank: §f" + String.format("%.2f", faction.get().bankBalance()));
        player.sendMessage("§7Your balance: §f" + String.format("%.2f", EconomyIntegration.balance(player)));
        return true;
    }
}
