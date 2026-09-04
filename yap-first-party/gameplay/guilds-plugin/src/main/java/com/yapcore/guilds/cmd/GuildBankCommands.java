package com.yapcore.guilds.cmd;

import com.yapcore.guilds.integration.EconomyIntegration;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;

final class GuildBankCommands {

    private final GuildCommandSupport ctx;

    GuildBankCommands(GuildCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean deposit(Player player, String[] args) {
        if (!ctx.config.bankEnabled()) {
            player.sendMessage("§cGuild bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g deposit <amount>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
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
        ctx.guilds.bankDeposit(member.get().guildId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aDeposited §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean withdraw(Player player, String[] args) {
        if (!ctx.config.bankEnabled()) {
            player.sendMessage("§cGuild bank is disabled.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUsage: /g withdraw <amount>");
            return true;
        }
        var member = ctx.guilds.member(player.getUniqueId());
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
        ctx.guilds.bankWithdraw(member.get().guildId(), player.getUniqueId(), amount).thenRun(() ->
                YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§aWithdrew §f" + amount + ".")))
                .exceptionally(ex -> {
                    YapSched.entity(ctx.plugin, player, () -> player.sendMessage("§c" + GuildCommandSupport.rootMessage(ex)));
                    return null;
                });
        return true;
    }

    boolean bank(Player player) {
        var guild = ctx.guilds.findByPlayer(player.getUniqueId());
        if (guild.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.");
            return true;
        }
        player.sendMessage("§7Guild bank: §f" + String.format("%.2f", guild.get().bankBalance())
                + "§7/§f" + String.format("%.0f", ctx.guilds.bankCap(guild.get().id())));
        player.sendMessage("§7Your balance: §f" + String.format("%.2f", EconomyIntegration.balance(player)));
        return true;
    }

}
