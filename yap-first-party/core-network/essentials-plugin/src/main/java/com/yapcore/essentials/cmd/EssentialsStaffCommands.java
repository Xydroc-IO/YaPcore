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
import com.yapcore.essentials.weather.WorldWeather;
import com.yapcore.moderation.ModerationService;
import com.yapcore.moderation.Punishment;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Locale;
import java.net.InetSocketAddress;
import java.util.stream.Collectors;


final class EssentialsStaffCommands {
    private final EssentialsCommandSupport ctx;

    EssentialsStaffCommands(EssentialsCommandSupport ctx) {
        this.ctx = ctx;
    }

    boolean list(CommandSender sender) {
        if (ctx.disabled(sender, "list")) {
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

    boolean broadcast(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "broadcast")) {
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
        String message = EssentialsCommandSupport.join(args);
        String line = ctx.config.broadcastFormat().replace("{message}", message).replace('&', '§');
        Bukkit.broadcastMessage(line);
        return true;
    }

    boolean rules(CommandSender sender) {
        if (ctx.disabled(sender, "rules")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.rules")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        for (String line : ctx.config.rules()) {
            sender.sendMessage(line.replace('&', '§'));
        }
        return true;
    }

    boolean motd(CommandSender sender) {
        if (ctx.disabled(sender, "motd")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.motd")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        for (String line : ctx.config.motd()) {
            sender.sendMessage(line.replace('&', '§'));
        }
        return true;
    }

    boolean socialSpy(CommandSender sender) {
        if (ctx.disabled(sender, "staff")) {
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("YaPChat") != null) {
            sender.sendMessage("§ePM social spy is handled by YaPChat — grant §fyapchat.socialspy§e.");
            return true;
        }
        if (!ctx.requirePlayer(sender)) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.ctx.staff.socialspy")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Player player = (Player) sender;
        boolean on = ctx.staff.toggleSocialSpy(player);
        player.sendMessage(on ? "§aSocial spy enabled." : "§eSocial spy disabled.");
        return true;
    }

    boolean freeze(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "staff")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.ctx.staff.freeze")) {
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
        boolean frozen = ctx.staff.toggleFreeze(target);
        EssentialsCommandSupport.msg(sender, target, frozen ? "Frozen." : "Unfrozen.");
        return true;
    }

    boolean check(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "staff")) {
            return true;
        }
        if (!sender.hasPermission("yapessentials.ctx.staff.check")) {
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
            mod.history(offline.getUniqueId(), 5).thenAccept(list -> YapSched.global(ctx.plugin, () -> {
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

    void printCheck(CommandSender sender, Player target) {
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
                + " §7Vanish: §f" + ctx.vanish.isVanished(target.getUniqueId())
                + " §7Frozen: §f" + ctx.staff.isFrozen(target.getUniqueId()));
        ModerationService mod = Bukkit.getServicesManager().load(ModerationService.class);
        if (mod != null) {
            mod.activeBan(target.getUniqueId()).ifPresentOrElse(
                    ban -> sender.sendMessage("§cActive ban: §f" + ban.reason()),
                    () -> sender.sendMessage("§aNo active ban."));
            mod.activeMute(target.getUniqueId()).ifPresentOrElse(
                    mute -> sender.sendMessage("§eActive mute: §f" + mute.reason()),
                    () -> sender.sendMessage("§aNo active mute."));
            mod.history(target.getUniqueId(), 5).thenAccept(list -> YapSched.global(ctx.plugin, () -> {
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

    boolean yapess(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapessentials.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            ctx.plugin.reloadEssentials();
            sender.sendMessage("§aYaPEssentials reloaded.");
            return true;
        }
        sender.sendMessage("§e/yapess reload");
        return true;
    }

    boolean weather(CommandSender sender, String[] args) {
        if (ctx.disabled(sender, "weather")) {
            return true;
        }
        // Prefer YaPDisasters when installed (Phase 1 home for extremes + GUI).
        var disasters = Bukkit.getPluginManager().getPlugin("YaPDisasters");
        if (disasters != null && disasters.isEnabled()) {
            String joined = args.length == 0 ? "gui" : String.join(" ", args);
            return Bukkit.dispatchCommand(sender, "yapdisaster " + joined);
        }
        if (!sender.hasPermission("yapessentials.weather")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§eInstall YaPDisasters for the weather GUI.");
            sender.sendMessage("§e/weather <clear|rain|thunder|lock|unlock> [seconds] [world]");
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if ("lock".equals(first) || "unlock".equals(first)) {
            World world = resolveWorld(sender, args.length >= 2 ? args[1] : null);
            if (world == null) {
                return true;
            }
            boolean enable = "unlock".equals(first);
            WorldWeather.setCycle(ctx.plugin, world, enable);
            sender.sendMessage(enable
                    ? "§aWeather cycle unlocked in §f" + world.getName() + "§a."
                    : "§eWeather cycle locked in §f" + world.getName() + "§e.");
            return true;
        }

        WorldWeather.Mode mode = WorldWeather.parseMode(first);
        if (mode == null) {
            sender.sendMessage("§e/weather <clear|rain|thunder|lock|unlock> [seconds] [world]");
            sender.sendMessage("§7Extreme weather needs YaPDisasters.");
            return true;
        }

        int duration = WorldWeather.DEFAULT_SECONDS;
        String worldArg = null;
        if (args.length >= 2) {
            try {
                duration = Integer.parseInt(args[1]);
                if (args.length >= 3) {
                    worldArg = args[2];
                }
            } catch (NumberFormatException e) {
                worldArg = args[1];
                if (args.length >= 3) {
                    try {
                        duration = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage("§cInvalid duration (seconds).");
                        return true;
                    }
                }
            }
        }
        duration = Math.max(1, Math.min(duration, WorldWeather.LOCK_SECONDS));

        World world = resolveWorld(sender, worldArg);
        if (world == null) {
            return true;
        }
        WorldWeather.apply(ctx.plugin, world, mode, duration);
        sender.sendMessage("§aWeather set to §f" + mode.name().toLowerCase(Locale.ROOT)
                + " §ain §f" + world.getName()
                + " §afor §f" + duration + "s§a.");
        return true;
    }

    private World resolveWorld(CommandSender sender, String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage("§cUnknown world: §f" + worldName);
                return null;
            }
            return world;
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        World first = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (first == null) {
            sender.sendMessage("§cNo worlds loaded.");
        }
        return first;
    }
}
