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


final class EssentialsPlayerCommands {
    private final EssentialsCommandSupport ctx;

    EssentialsPlayerCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean gamemode(CommandSender sender, String[] args, GameMode forced) {
        if (ctx.disabled(sender, "gamemode")) {
            return true;
        }
        GameMode mode = forced;
        int playerArg = 0;
        if (mode == null) {
            if (args.length < 1) {
                sender.sendMessage("§e/gm <0|1|2|3|s|c|a|sp> [player]");
                sender.sendMessage("§7Also §f/gms §7/ §f/gmc §7/ §f/gma §7/ §f/gmsp");
                return true;
            }
            mode = EssentialsCommandSupport.parseGameMode(args[0]);
            if (mode == null) {
                sender.sendMessage("§cUnknown game mode. Use 0/1/2/3 or survival/creative/adventure/spectator.");
                return true;
            }
            playerArg = 1;
        }
        Player target = ctx.targetPlayer(sender, args, playerArg, "yapessentials.gamemode");
        if (target == null) {
            return true;
        }
        GameMode applied = mode;
        YapSched.entity(ctx.plugin, target, () -> {
            target.setGameMode(applied);
            EssentialsCommandSupport.msg(sender, target, "Game mode set to " + pretty(applied) + ".");
        });
        return true;
    }

    private static String pretty(GameMode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    boolean item(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "item")) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/i <item> [amount] [player]");
            return true;
        }
        Material material = EssentialsCommandSupport.matchItem(args[0]);
        if (material == null) {
            sender.sendMessage("§cUnknown item: " + args[0]);
            return true;
        }
        int amount = 1;
        int playerArg = -1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                playerArg = 2;
            } catch (NumberFormatException e) {
                playerArg = 1;
            }
        }
        if (amount < 1 || amount > 2304) {
            sender.sendMessage("§cAmount must be 1–2304.");
            return true;
        }
        String[] targetArgs = (playerArg >= 0 && args.length > playerArg) ? args : new String[0];
        int otherIndex = (playerArg >= 0 && args.length > playerArg) ? playerArg : 0;
        Player target = ctx.targetPlayer(sender, targetArgs, otherIndex, "yapessentials.item");
        if (target == null) {
            return true;
        }
        int give = amount;
        YapSched.entity(ctx.plugin, target, () -> {
            int left = give;
            while (left > 0) {
                int stack = Math.min(left, Math.max(1, material.getMaxStackSize()));
                var leftover = target.getInventory().addItem(new ItemStack(material, stack));
                leftover.values().forEach(drop ->
                        target.getWorld().dropItemNaturally(target.getLocation(), drop));
                left -= stack;
            }
            EssentialsCommandSupport.msg(sender, target,
                    "Received " + give + "× " + material.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        });
        return true;
    }

    boolean fly(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "fly")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.fly");
        if (target == null) {
            return true;
        }
        target.setAllowFlight(!target.getAllowFlight());
        target.setFlying(target.getAllowFlight());
        EssentialsCommandSupport.msg(sender, target, "Flight " + (target.getAllowFlight() ? "enabled" : "disabled"));
        return true;
    }

    boolean god(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "god")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.god");
        if (target == null) {
            return true;
        }
        boolean invulnerable = !target.isInvulnerable();
        target.setInvulnerable(invulnerable);
        EssentialsCommandSupport.msg(sender, target, "God mode " + (invulnerable ? "enabled" : "disabled"));
        return true;
    }

    boolean speed(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "speed")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.speed")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/speed <0-10> [fly|walk]");
            return true;
        }
        float speed;
        try {
            speed = Float.parseFloat(args[0]) / 10f;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid speed.");
            return true;
        }
        Player player = (Player) sender;
        boolean fly = args.length >= 2 && args[1].equalsIgnoreCase("fly");
        if (fly) {
            player.setFlySpeed(EssentialsCommandSupport.clamp(speed));
        } else {
            player.setWalkSpeed(EssentialsCommandSupport.clamp(speed));
        }
        player.sendMessage("§aSpeed updated.");
        return true;
    }

    boolean heal(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "heal")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.heal");
        if (target == null) {
            return true;
        }
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        EssentialsCommandSupport.msg(sender, target, "Healed.");
        return true;
    }

    boolean feed(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "feed")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.feed");
        if (target == null) {
            return true;
        }
        target.setFoodLevel(20);
        target.setSaturation(20f);
        EssentialsCommandSupport.msg(sender, target, "Fed.");
        return true;
    }

    boolean repair(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "repair")) {
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.repair")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        boolean all = args.length >= 1 && args[0].equalsIgnoreCase("all");
        if (all) {
            for (ItemStack item : player.getInventory().getContents()) {
                EssentialsCommandSupport.repairItem(item);
            }
            for (ItemStack item : player.getInventory().getArmorContents()) {
                EssentialsCommandSupport.repairItem(item);
            }
        } else {
            EssentialsCommandSupport.repairItem(player.getInventory().getItemInMainHand());
        }
        player.sendMessage("§aRepaired.");
        return true;
    }

    boolean clear(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "clear")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.clear");
        if (target == null) {
            return true;
        }
        target.getInventory().clear();
        EssentialsCommandSupport.msg(sender, target, "Inventory cleared.");
        return true;
    }

    boolean vanish(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "vanish")) {
            return true;
        }
        Player target = ctx.targetPlayer(sender, args, 0, "yapessentials.vanish");
        if (target == null) {
            return true;
        }
        boolean hidden = ctx.vanish.toggle(target);
        EssentialsCommandSupport.msg(sender, target, "Vanish " + (hidden ? "enabled" : "disabled"));
        return true;
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
                sender.sendMessage("§cNo permission.");
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
            sender.sendMessage("§cNo permission.");
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
            sender.sendMessage("§cNo permission.");
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
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        ctx.back.remember(player);
        player.setHealth(0.0);
        return true;
    }
}
