package com.yapcore.essentials.cmd;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.essentials.store.AfkService;
import com.yapcore.essentials.store.BackStore;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.store.StaffService;
import com.yapcore.essentials.store.TpaService;
import com.yapcore.essentials.store.VanishService;
import com.yapcore.essentials.util.TeleportHelper;
import com.yapcore.moderation.ModerationService;
import com.yapcore.moderation.Punishment;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Locale;
import java.net.InetSocketAddress;


final class EssentialsTeleportCommands {
    private final EssentialsCommandSupport ctx;

    EssentialsTeleportCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean spawn(CommandSender sender) {
        if (ctx.disabled(sender, "spawn")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.spawn")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        Location spawn = ctx.spawnStore.spawn();
        if (spawn == null) {
            player.sendMessage("§cSpawn not set.");
            return true;
        }
        ctx.back.remember(player);
        TeleportHelper.teleport(ctx.plugin, player, spawn);
        player.sendMessage("§aTeleported to spawn.");
        return true;
    }

    boolean setSpawn(CommandSender sender) {
        if (ctx.disabled(sender, "spawn")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.setspawn")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        ctx.spawnStore.setSpawn(player.getLocation());
        player.sendMessage("§aSpawn set.");
        return true;
    }

    boolean back(CommandSender sender) {
        if (ctx.disabled(sender, "back")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.back")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        Location loc = ctx.back.back(player);
        if (loc == null) {
            player.sendMessage("§cNo back location.");
            return true;
        }
        TeleportHelper.teleport(ctx.plugin, player, loc);
        player.sendMessage("§aTeleported ctx.back.");
        return true;
    }

    boolean tpa(CommandSender sender, String[] args, boolean here) {
        if (ctx.disabled(sender, "tpa")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.tpa")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(here ? "§e/tpahere <player>" : "§e/tpa <player>");
            return true;
        }
        Player requester = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            requester.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.tpa.request(requester, target, here, ctx.config.tpaTimeoutSeconds());
        return true;
    }

    boolean tpAccept(CommandSender sender) {
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        Player target = (Player) sender;
        TpaService.Request req = ctx.tpa.pending(target.getUniqueId());
        if (req == null) {
            target.sendMessage("§cNo pending request.");
            return true;
        }
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null) {
            target.sendMessage("§cRequester offline.");
            ctx.tpa.clear(target.getUniqueId());
            return true;
        }
        ctx.back.remember(req.here() ? target : requester);
        if (req.here()) {
            TeleportHelper.teleport(ctx.plugin, target, requester.getLocation());
        } else {
            TeleportHelper.teleport(ctx.plugin, requester, target.getLocation());
        }
        ctx.tpa.clear(target.getUniqueId());
        target.sendMessage("§aRequest accepted.");
        requester.sendMessage("§aTeleporting...");
        return true;
    }

    boolean tpDeny(CommandSender sender) {
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        Player target = (Player) sender;
        if (ctx.tpa.pending(target.getUniqueId()) == null) {
            target.sendMessage("§cNo pending request.");
            return true;
        }
        ctx.tpa.clear(target.getUniqueId());
        target.sendMessage("§eRequest denied.");
        return true;
    }

    boolean tp(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "teleport")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.teleport")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/tp <player> [target]");
            return true;
        }
        Player executor = (Player) sender;
        Player first = Bukkit.getPlayer(args[0]);
        if (first == null) {
            executor.sendMessage("§cPlayer not online.");
            return true;
        }
        if (args.length >= 2) {
            Player second = Bukkit.getPlayer(args[1]);
            if (second == null) {
                executor.sendMessage("§cTarget not online.");
                return true;
            }
            ctx.back.remember(first);
            TeleportHelper.teleport(ctx.plugin, first, second.getLocation());
            executor.sendMessage("§aTeleported §f" + first.getName() + " §ato §f" + second.getName());
            return true;
        }
        ctx.back.remember(executor);
        TeleportHelper.teleport(ctx.plugin, executor, first.getLocation());
        executor.sendMessage("§aTeleported to §f" + first.getName());
        return true;
    }

    boolean tpHere(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "teleport")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.teleport")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/tphere <player>");
            return true;
        }
        Player executor = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            executor.sendMessage("§cPlayer not online.");
            return true;
        }
        ctx.back.remember(target);
        TeleportHelper.teleport(ctx.plugin, target, executor.getLocation());
        executor.sendMessage("§aTeleported §f" + target.getName() + " §ato you.");
        return true;
    }
}
