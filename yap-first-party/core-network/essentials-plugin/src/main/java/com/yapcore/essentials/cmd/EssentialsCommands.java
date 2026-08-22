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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.InetSocketAddress;
import java.util.stream.Collectors;

public final class EssentialsCommands implements CommandExecutor, TabCompleter {

    private final EssentialsPlugin plugin;
    private final EssentialsConfig config;
    private final SpawnStore spawnStore;
    private final BackStore back;
    private final TpaService tpa;
    private final AfkService afk;
    private final VanishService vanish;
    private final StaffService staff;

    public EssentialsCommands(EssentialsPlugin plugin, EssentialsConfig config, SpawnStore spawnStore,
                              BackStore back, TpaService tpa, AfkService afk, VanishService vanish,
                              StaffService staff) {
        this.plugin = plugin;
        this.config = config;
        this.spawnStore = spawnStore;
        this.back = back;
        this.tpa = tpa;
        this.afk = afk;
        this.vanish = vanish;
        this.staff = staff;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "spawn" -> spawn(sender);
            case "setspawn" -> setSpawn(sender);
            case "back" -> back(sender);
            case "tpa" -> tpa(sender, args, false);
            case "tpahere" -> tpa(sender, args, true);
            case "tpaccept", "tpyes" -> tpAccept(sender);
            case "tpdeny", "tpno" -> tpDeny(sender);
            case "fly" -> fly(sender, args);
            case "god" -> god(sender, args);
            case "speed" -> speed(sender, args);
            case "heal" -> heal(sender, args);
            case "feed" -> feed(sender, args);
            case "repair" -> repair(sender, args);
            case "clear" -> clear(sender, args);
            case "vanish", "v" -> vanish(sender, args);
            case "invsee" -> invsee(sender, args);
            case "echest", "enderchest", "ec" -> echest(sender, args);
            case "nick" -> nick(sender, args);
            case "afk" -> afk(sender);
            case "list", "online", "who" -> list(sender);
            case "ptime" -> ptime(sender, args);
            case "pweather" -> pweather(sender, args);
            case "broadcast", "bc", "say" -> broadcast(sender, args);
            case "rules" -> rules(sender);
            case "motd" -> motd(sender);
            case "suicide", "kill" -> suicide(sender);
            case "hat" -> hat(sender);
            case "tp" -> tp(sender, args);
            case "tphere", "s" -> tpHere(sender, args);
            case "socialspy", "ss" -> socialSpy(sender);
            case "freeze" -> freeze(sender, args);
            case "check" -> check(sender, args);
            case "yapess" -> yapess(sender, args);
            default -> false;
        };
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage("Players only.");
        return false;
    }

    private boolean disabled(CommandSender sender, String feature) {
        if (config.feature(feature)) {
            return false;
        }
        sender.sendMessage("§cThat feature is disabled on this server.");
        return true;
    }

    private boolean spawn(CommandSender sender) {
        if (disabled(sender, "spawn")) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.spawn")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        Location spawn = spawnStore.spawn();
        if (spawn == null) {
            player.sendMessage("§cSpawn not set.");
            return true;
        }
        back.remember(player);
        TeleportHelper.teleport(plugin, player, spawn);
        player.sendMessage("§aTeleported to spawn.");
        return true;
    }

    private boolean setSpawn(CommandSender sender) {
        if (disabled(sender, "spawn")) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.setspawn")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        spawnStore.setSpawn(player.getLocation());
        player.sendMessage("§aSpawn set.");
        return true;
    }

    private boolean back(CommandSender sender) {
        if (disabled(sender, "back")) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.back")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        Location loc = back.back(player);
        if (loc == null) {
            player.sendMessage("§cNo back location.");
            return true;
        }
        TeleportHelper.teleport(plugin, player, loc);
        player.sendMessage("§aTeleported back.");
        return true;
    }

    private boolean tpa(CommandSender sender, String[] args, boolean here) {
        if (disabled(sender, "tpa")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
        tpa.request(requester, target, here, config.tpaTimeoutSeconds());
        return true;
    }

    private boolean tpAccept(CommandSender sender) {
        if (!requirePlayer(sender)) {
            return true;
        }
        Player target = (Player) sender;
        TpaService.Request req = tpa.pending(target.getUniqueId());
        if (req == null) {
            target.sendMessage("§cNo pending request.");
            return true;
        }
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null) {
            target.sendMessage("§cRequester offline.");
            tpa.clear(target.getUniqueId());
            return true;
        }
        back.remember(req.here() ? target : requester);
        if (req.here()) {
            TeleportHelper.teleport(plugin, target, requester.getLocation());
        } else {
            TeleportHelper.teleport(plugin, requester, target.getLocation());
        }
        tpa.clear(target.getUniqueId());
        target.sendMessage("§aRequest accepted.");
        requester.sendMessage("§aTeleporting...");
        return true;
    }

    private boolean tpDeny(CommandSender sender) {
        if (!requirePlayer(sender)) {
            return true;
        }
        Player target = (Player) sender;
        if (tpa.pending(target.getUniqueId()) == null) {
            target.sendMessage("§cNo pending request.");
            return true;
        }
        tpa.clear(target.getUniqueId());
        target.sendMessage("§eRequest denied.");
        return true;
    }

    private boolean fly(CommandSender sender, String[] args) {
        if (disabled(sender, "fly")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.fly");
        if (target == null) {
            return true;
        }
        target.setAllowFlight(!target.getAllowFlight());
        target.setFlying(target.getAllowFlight());
        msg(sender, target, "Flight " + (target.getAllowFlight() ? "enabled" : "disabled"));
        return true;
    }

    private boolean god(CommandSender sender, String[] args) {
        if (disabled(sender, "god")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.god");
        if (target == null) {
            return true;
        }
        boolean invulnerable = !target.isInvulnerable();
        target.setInvulnerable(invulnerable);
        msg(sender, target, "God mode " + (invulnerable ? "enabled" : "disabled"));
        return true;
    }

    private boolean speed(CommandSender sender, String[] args) {
        if (disabled(sender, "speed")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
            player.setFlySpeed(clamp(speed));
        } else {
            player.setWalkSpeed(clamp(speed));
        }
        player.sendMessage("§aSpeed updated.");
        return true;
    }

    private boolean heal(CommandSender sender, String[] args) {
        if (disabled(sender, "heal")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.heal");
        if (target == null) {
            return true;
        }
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        msg(sender, target, "Healed.");
        return true;
    }

    private boolean feed(CommandSender sender, String[] args) {
        if (disabled(sender, "feed")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.feed");
        if (target == null) {
            return true;
        }
        target.setFoodLevel(20);
        target.setSaturation(20f);
        msg(sender, target, "Fed.");
        return true;
    }

    private boolean repair(CommandSender sender, String[] args) {
        if (disabled(sender, "repair")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
                repairItem(item);
            }
            for (ItemStack item : player.getInventory().getArmorContents()) {
                repairItem(item);
            }
        } else {
            repairItem(player.getInventory().getItemInMainHand());
        }
        player.sendMessage("§aRepaired.");
        return true;
    }

    private boolean clear(CommandSender sender, String[] args) {
        if (disabled(sender, "clear")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.clear");
        if (target == null) {
            return true;
        }
        target.getInventory().clear();
        msg(sender, target, "Inventory cleared.");
        return true;
    }

    private boolean vanish(CommandSender sender, String[] args) {
        if (disabled(sender, "vanish")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 0, "yapessentials.vanish");
        if (target == null) {
            return true;
        }
        boolean hidden = vanish.toggle(target);
        msg(sender, target, "Vanish " + (hidden ? "enabled" : "disabled"));
        return true;
    }

    private boolean invsee(CommandSender sender, String[] args) {
        if (disabled(sender, "invsee")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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

    private boolean echest(CommandSender sender, String[] args) {
        if (disabled(sender, "echest")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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

    private boolean nick(CommandSender sender, String[] args) {
        if (disabled(sender, "nick")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
        msg(sender, target, "Nick updated.");
        return true;
    }

    private boolean afk(CommandSender sender) {
        if (disabled(sender, "afk")) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.afk")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        boolean nowAfk = afk.toggle(player);
        player.sendMessage(nowAfk ? "§7You are now AFK." : "§aYou are no longer AFK.");
        return true;
    }

    private boolean list(CommandSender sender) {
        if (disabled(sender, "list")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.list")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.joining(", "));
        sender.sendMessage("§aOnline (" + Bukkit.getOnlinePlayers().size() + "): §f" + names);
        return true;
    }

    private boolean ptime(CommandSender sender, String[] args) {
        if (disabled(sender, "ptime")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 1, "yapessentials.ptime");
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
        msg(sender, target, "Player time updated.");
        return true;
    }

    private boolean pweather(CommandSender sender, String[] args) {
        if (disabled(sender, "pweather")) {
            return true;
        }
        Player target = targetPlayer(sender, args, 1, "yapessentials.pweather");
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
        msg(sender, target, "Player weather updated.");
        return true;
    }

    private boolean broadcast(CommandSender sender, String[] args) {
        if (disabled(sender, "broadcast")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.broadcast")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/broadcast <message>");
            return true;
        }
        String message = join(args);
        String line = config.broadcastFormat().replace("{message}", message).replace('&', '§');
        Bukkit.broadcastMessage(line);
        return true;
    }

    private boolean rules(CommandSender sender) {
        if (disabled(sender, "rules")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.rules")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        for (String line : config.rules()) {
            sender.sendMessage(line.replace('&', '§'));
        }
        return true;
    }

    private boolean motd(CommandSender sender) {
        if (disabled(sender, "motd")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.motd")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        for (String line : config.motd()) {
            sender.sendMessage(line.replace('&', '§'));
        }
        return true;
    }

    private boolean suicide(CommandSender sender) {
        if (disabled(sender, "suicide")) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.suicide")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        back.remember(player);
        player.setHealth(0.0);
        return true;
    }

    private boolean hat(CommandSender sender) {
        if (disabled(sender, "hat")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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

    private boolean tp(CommandSender sender, String[] args) {
        if (disabled(sender, "teleport")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
            back.remember(first);
            TeleportHelper.teleport(plugin, first, second.getLocation());
            executor.sendMessage("§aTeleported §f" + first.getName() + " §ato §f" + second.getName());
            return true;
        }
        back.remember(executor);
        TeleportHelper.teleport(plugin, executor, first.getLocation());
        executor.sendMessage("§aTeleported to §f" + first.getName());
        return true;
    }

    private boolean tpHere(CommandSender sender, String[] args) {
        if (disabled(sender, "teleport")) {
            return true;
        }
        if (!requirePlayer(sender)) {
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
        back.remember(target);
        TeleportHelper.teleport(plugin, target, executor.getLocation());
        executor.sendMessage("§aTeleported §f" + target.getName() + " §ato you.");
        return true;
    }

    private boolean socialSpy(CommandSender sender) {
        if (disabled(sender, "staff")) {
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("YaPChat") != null) {
            sender.sendMessage("§ePM social spy is handled by YaPChat — grant §fyapchat.socialspy§e.");
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.staff.socialspy")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        boolean on = staff.toggleSocialSpy(player);
        player.sendMessage(on ? "§aSocial spy enabled." : "§eSocial spy disabled.");
        return true;
    }

    private boolean freeze(CommandSender sender, String[] args) {
        if (disabled(sender, "staff")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.staff.freeze")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/freeze <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        boolean frozen = staff.toggleFreeze(target);
        msg(sender, target, frozen ? "Frozen." : "Unfrozen.");
        return true;
    }

    private boolean check(CommandSender sender, String[] args) {
        if (disabled(sender, "staff")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.staff.check")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/check <player>");
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        Player online = offline.getPlayer();
        if (online != null && online.isOnline()) {
            printCheck(sender, online);
            return true;
        }
        sender.sendMessage("§6Offline check — §f" + offline.getName());
        sender.sendMessage("§7UUID: §f" + offline.getUniqueId());
        ModerationService mod = Bukkit.getServicesManager().load(ModerationService.class);
        if (mod != null) {
            mod.history(offline.getUniqueId(), 5).thenAccept(list -> YapSched.global(plugin, () -> {
                sender.sendMessage("§6Recent moderation (last 5):");
                if (list.isEmpty()) {
                    sender.sendMessage("§7(none)");
                    return;
                }
                for (Punishment p : list) {
                    sender.sendMessage("§7- §f" + p.type() + " §7by §f" + p.actorName()
                            + " §7— §f" + p.reason());
                }
            }));
        }
        return true;
    }

    private void printCheck(CommandSender sender, Player target) {
        sender.sendMessage("§6Check — §f" + target.getName());
        sender.sendMessage("§7UUID: §f" + target.getUniqueId());
        if (target.getAddress() != null) {
            InetSocketAddress addr = target.getAddress();
            sender.sendMessage("§7IP: §f" + addr.getAddress().getHostAddress());
        }
        Location loc = target.getLocation();
        sender.sendMessage("§7World: §f" + loc.getWorld().getName()
                + " §7@ §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        sender.sendMessage("§7GM: §f" + target.getGameMode()
                + " §7HP: §f" + String.format(Locale.ROOT, "%.1f/%.1f", target.getHealth(), target.getMaxHealth())
                + " §7Food: §f" + target.getFoodLevel());
        sender.sendMessage("§7Fly: §f" + target.getAllowFlight()
                + " §7God: §f" + target.isInvulnerable()
                + " §7Vanish: §f" + vanish.isVanished(target.getUniqueId())
                + " §7Frozen: §f" + staff.isFrozen(target.getUniqueId()));
        ModerationService mod = Bukkit.getServicesManager().load(ModerationService.class);
        if (mod != null) {
            mod.activeBan(target.getUniqueId()).ifPresentOrElse(
                    ban -> sender.sendMessage("§cActive ban: §f" + ban.reason()),
                    () -> sender.sendMessage("§aNo active ban."));
            mod.activeMute(target.getUniqueId()).ifPresentOrElse(
                    mute -> sender.sendMessage("§eActive mute: §f" + mute.reason()),
                    () -> sender.sendMessage("§aNo active mute."));
            mod.history(target.getUniqueId(), 5).thenAccept(list -> YapSched.global(plugin, () -> {
                sender.sendMessage("§6Recent moderation (last 5):");
                if (list.isEmpty()) {
                    sender.sendMessage("§7(none)");
                    return;
                }
                for (Punishment p : list) {
                    sender.sendMessage("§7- §f" + p.type() + " §7by §f" + p.actorName()
                            + " §7— §f" + p.reason());
                }
            }));
        }
        sender.sendMessage("§7Inventory rollback: §f/yapprotect lookup user " + target.getName()
                + " §7then §f/yapprotect rollback <id>");
    }

    private boolean yapess(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapessentials.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadEssentials();
            sender.sendMessage("§aYaPEssentials reloaded.");
            return true;
        }
        sender.sendMessage("§e/yapess reload");
        return true;
    }

    private Player targetPlayer(CommandSender sender, String[] args, int otherArgIndex, String selfPerm) {
        if (sender instanceof Player player) {
            if (args.length > otherArgIndex && !sender.hasPermission(selfPerm)) {
                sender.sendMessage("§cNo permission for others.");
                return null;
            }
            if (args.length > otherArgIndex) {
                Player other = Bukkit.getPlayer(args[otherArgIndex]);
                if (other == null) {
                    sender.sendMessage("§cPlayer not online.");
                    return null;
                }
                return other;
            }
            if (!sender.hasPermission(selfPerm)) {
                sender.sendMessage("§cNo permission.");
                return null;
            }
            return player;
        }
        if (args.length <= otherArgIndex) {
            sender.sendMessage("Console must specify a player.");
            return null;
        }
        Player other = Bukkit.getPlayer(args[otherArgIndex]);
        if (other == null) {
            sender.sendMessage("§cPlayer not online.");
            return null;
        }
        return other;
    }

    private static void repairItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
        }
    }

    private static float clamp(float speed) {
        return Math.max(0.0f, Math.min(1.0f, speed));
    }

    private static void msg(CommandSender sender, Player target, String message) {
        if (sender.equals(target)) {
            target.sendMessage("§a" + message);
        } else {
            sender.sendMessage("§a" + target.getName() + ": " + message);
            target.sendMessage("§a" + message);
        }
    }

    private static String join(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
