package com.yapcore.essentials.cmd;

import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** Nick / AFK / personal time-weather / hat / suicide / near player commands. */
final class EssentialsPlayerMiscCommands {
    private final EssentialsCommandSupport ctx;

    EssentialsPlayerMiscCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean nick(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "nick")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/nick <name|off> [player]");
            return true;
        }
        Player target;
        String nickArg;
        if (args.length >= 2 && sender.hasPermission("yapessentials.nick.others")) {
            target = Bukkit.getPlayer(args[1]);
            nickArg = args[0];
        } else {
            if (!sender.hasPermission("yapessentials.nick")) {
                YapMessages.noPermission(sender);
                return true;
            }
            target = (Player) sender;
            nickArg = args[0];
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        if ("off".equalsIgnoreCase(nickArg)) {
            target.setDisplayName(target.getName());
            target.setPlayerListName(target.getName());
        } else {
            String colored = nickArg.replace('&', '§');
            target.setDisplayName(colored);
            target.setPlayerListName(colored);
        }
        EssentialsCommandSupport.msg(sender, target, "Nick updated.");
        return true;
    }

    boolean afk(CommandSender sender) {
        if (ctx.disabled(sender, "afk")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.afk")) {
            YapMessages.noPermission(sender);
            return true;
        }
        Player player = (Player) sender;
        boolean nowAfk = ctx.afk.toggle(player);
        player.sendMessage(nowAfk ? "§7You are now AFK." : "§aYou are no longer AFK.");
        return true;
    }

    boolean ptime(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "ptime")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 1, "yapessentials.ptime");
        if (target == null) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/ptime <time|reset> [player]");
            return true;
        }
        if ("reset".equalsIgnoreCase(args[0])) {
            target.resetPlayerTime();
        } else {
            long time;
            try {
                time = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid time.");
                return true;
            }
            target.setPlayerTime(time, false);
        }
        EssentialsCommandSupport.msg(sender, target, "Player time updated.");
        return true;
    }

    boolean pweather(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "pweather")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 1, "yapessentials.pweather");
        if (target == null) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/pweather <clear|rain|reset> [player]");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset" -> target.resetPlayerWeather();
            case "rain", "storm" -> target.setPlayerWeather(WeatherType.DOWNFALL);
            default -> target.setPlayerWeather(WeatherType.CLEAR);
        }
        EssentialsCommandSupport.msg(sender, target, "Player weather updated.");
        return true;
    }

    boolean hat(CommandSender sender) {
        if (ctx.disabled(sender, "hat")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.hat")) {
            YapMessages.noPermission(sender);
            return true;
        }
        Player player = (Player) sender;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHold an item.");
            return true;
        }
        ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(hand.clone());
        player.getInventory().setItemInMainHand(helmet != null ? helmet : new ItemStack(Material.AIR));
        player.sendMessage("§aHat equipped.");
        return true;
    }

    boolean suicide(CommandSender sender) {
        if (ctx.disabled(sender, "suicide")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.suicide")) {
            YapMessages.noPermission(sender);
            return true;
        }
        Player player = (Player) sender;
        ctx.back.remember(player);
        player.setHealth(0.0);
        return true;
    }

    boolean near(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "near")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.near")) {
            YapMessages.noPermission(sender);
            return true;
        }
        Player player = (Player) sender;
        double radius = 100.0;
        if (args.length >= 1) {
            try {
                radius = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid radius.");
                return true;
            }
        }
        radius = Math.max(1.0, Math.min(radius, 500.0));
        double r2 = radius * radius;
        Location origin = player.getLocation();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!other.getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distSq = other.getLocation().distanceSquared(origin);
            if (distSq <= r2) {
                lines.add("§f" + other.getName() + " §7(" + (int) Math.sqrt(distSq) + "m)");
            }
        }
        if (lines.isEmpty()) {
            player.sendMessage("§7No players within §f" + (int) radius + "§7m.");
            return true;
        }
        player.sendMessage("§6Nearby (§f" + (int) radius + "§6m):");
        for (String line : lines) {
            player.sendMessage(line);
        }
        return true;
    }
}
