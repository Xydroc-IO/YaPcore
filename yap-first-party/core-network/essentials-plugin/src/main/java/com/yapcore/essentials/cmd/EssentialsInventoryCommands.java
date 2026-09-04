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


final class EssentialsInventoryCommands {
    private final EssentialsCommandSupport ctx;

    EssentialsInventoryCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean invsee(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "invsee")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.invsee")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/invsee <player>");
            return true;
        }
        Player viewer = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            viewer.sendMessage("§cPlayer not online.");
            return true;
        }
        viewer.openInventory(target.getInventory());
        return true;
    }

    boolean echest(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "echest")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.echest")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/echest <player>");
            return true;
        }
        Player viewer = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            viewer.sendMessage("§cPlayer not online.");
            return true;
        }
        viewer.openInventory(target.getEnderChest());
        return true;
    }

    boolean workbench(CommandSender sender) {
        if (ctx.disabled(sender, "workbench")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.workbench")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        player.openWorkbench(player.getLocation(), true);
        return true;
    }

    boolean disposal(CommandSender sender) {
        if (ctx.disabled(sender, "disposal")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.disposal")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        var inv = Bukkit.createInventory(player, 36, "Disposal");
        player.openInventory(inv);
        return true;
    }
}
