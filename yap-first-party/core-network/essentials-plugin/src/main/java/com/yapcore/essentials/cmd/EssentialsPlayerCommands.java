package com.yapcore.essentials.cmd;

import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;


final class EssentialsPlayerCommands {
    private final EssentialsCommandSupport ctx;
    private final EssentialsPlayerMiscCommands misc;

    EssentialsPlayerCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
        this.misc = new EssentialsPlayerMiscCommands(ctx);
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
        if (args.length < 1) {
            sender.sendMessage("§e/speed [player] <0-10> [fly|walk]  §7or  §e/speed [player] <fly|walk> <0-10>");
            return true;
        }
        // Parse optional player + mode + level. Accepts:
        // /speed 5 fly | /speed fly 5 | /speed Steve 5 walk | /speed Steve fly 5
        Player namedTarget = null;
        String mode = "walk";
        Float level = null;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("fly") || arg.equalsIgnoreCase("walk")) {
                mode = arg.toLowerCase(java.util.Locale.ROOT);
                continue;
            }
            try {
                level = Float.parseFloat(arg);
                continue;
            } catch (NumberFormatException ignored) {
                // fall through — may be a player name
            }
            Player found = org.bukkit.Bukkit.getPlayerExact(arg);
            if (found == null) {
                found = org.bukkit.Bukkit.getPlayer(arg);
            }
            if (found == null) {
                YapMessages.send(sender, "&cUnknown player or invalid speed: &f{arg}", "arg", arg);
                return true;
            }
            if (namedTarget != null) {
                YapMessages.send(sender, "&cSpecify only one player.");
                return true;
            }
            namedTarget = found;
        }
        if (level == null) {
            sender.sendMessage("§cMissing speed value (0–10).");
            return true;
        }
        if (level < 0f || level > 10f) {
            sender.sendMessage("§cSpeed must be between 0 and 10.");
            return true;
        }

        Player target;
        if (namedTarget != null) {
            boolean self = sender instanceof Player p && p.getUniqueId().equals(namedTarget.getUniqueId());
            if (!self && !sender.hasPermission("yapessentials.speed.others")) {
                YapMessages.noPermission(sender, "yapessentials.speed.others");
                return true;
            }
            if (self && !sender.hasPermission("yapessentials.speed")) {
                YapMessages.noPermission(sender, "yapessentials.speed");
                return true;
            }
            target = namedTarget;
        } else {
            if (!(sender instanceof Player player)) {
                YapMessages.send(sender, "&cConsole must specify a player.");
                return true;
            }
            if (!sender.hasPermission("yapessentials.speed")) {
                YapMessages.noPermission(sender, "yapessentials.speed");
                return true;
            }
            target = player;
        }

        float speed = EssentialsCommandSupport.clamp(level / 10f);
        int shown = Math.round(level);
        if ("fly".equals(mode)) {
            target.setFlySpeed(speed);
            EssentialsCommandSupport.msg(sender, target,
                    "Fly speed set to " + shown + "/10");
        } else {
            target.setWalkSpeed(speed);
            EssentialsCommandSupport.msg(sender, target,
                    "Walk speed set to " + shown + "/10");
        }
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
            YapMessages.noPermission(sender);
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
        return misc.nick(sender, args);
    }

    boolean afk(CommandSender sender) {
        return misc.afk(sender);
    }

    boolean ptime(CommandSender sender, String[] args) {
        return misc.ptime(sender, args);
    }

    boolean pweather(CommandSender sender, String[] args) {
        return misc.pweather(sender, args);
    }

    boolean hat(CommandSender sender) {
        return misc.hat(sender);
    }

    boolean suicide(CommandSender sender) {
        return misc.suicide(sender);
    }

    boolean near(CommandSender sender, String[] args) {
        return misc.near(sender, args);
    }
}
