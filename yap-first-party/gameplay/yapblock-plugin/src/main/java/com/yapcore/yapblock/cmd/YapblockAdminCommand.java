package com.yapcore.yapblock.cmd;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;
import com.yapcore.yapblock.service.LocationTeleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YapblockAdminCommand implements CommandExecutor, TabCompleter {

    private final YapblockPlugin plugin;

    public YapblockAdminCommand(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    private IslandServiceImpl service() {
        return plugin.service();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("yapblock.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("/yapblock reload|tp|disband|setlevel", NamedTextColor.AQUA));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        IslandServiceImpl service = service();
        return switch (sub) {
            case "reload" -> {
                plugin.reloadYapblock();
                sender.sendMessage(Component.text("YaPblock reloaded.", NamedTextColor.GREEN));
                yield true;
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    yield true;
                }
                if (service == null) {
                    sender.sendMessage(Component.text("YaPblock not ready.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /yapblock tp <player|islandId>", NamedTextColor.RED));
                    yield true;
                }
                IslandSnapshot snap = resolveIsland(service, args[1]);
                if (snap == null) {
                    sender.sendMessage(Component.text("Island not found.", NamedTextColor.RED));
                    yield true;
                }
                World world = service.islandWorld();
                if (world == null) {
                    sender.sendMessage(Component.text("World not loaded.", NamedTextColor.RED));
                    yield true;
                }
                Location dest = service.index().homeLocation(world, snap);
                LocationTeleport.teleport(plugin, player, dest, ok ->
                        player.sendMessage(Component.text(
                                Boolean.TRUE.equals(ok) ? "Teleported." : "Teleport failed.",
                                Boolean.TRUE.equals(ok) ? NamedTextColor.GREEN : NamedTextColor.RED)));
                yield true;
            }
            case "disband" -> {
                if (service == null) {
                    sender.sendMessage(Component.text("YaPblock not ready.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /yapblock disband <player|islandId>", NamedTextColor.RED));
                    yield true;
                }
                IslandSnapshot snap = resolveIsland(service, args[1]);
                if (snap == null) {
                    sender.sendMessage(Component.text("Island not found.", NamedTextColor.RED));
                    yield true;
                }
                service.deleteOps().disband(snap.id(), sender instanceof Player p ? p : null).thenAccept(ok ->
                        sender.sendMessage(Component.text(
                                Boolean.TRUE.equals(ok) ? "Disbanded." : "Disband failed.",
                                Boolean.TRUE.equals(ok) ? NamedTextColor.GREEN : NamedTextColor.RED)));
                yield true;
            }
            case "setlevel" -> {
                if (service == null) {
                    sender.sendMessage(Component.text("YaPblock not ready.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text(
                            "Usage: /yapblock setlevel <player|islandId> <level>", NamedTextColor.RED));
                    yield true;
                }
                IslandSnapshot snap = resolveIsland(service, args[1]);
                if (snap == null) {
                    sender.sendMessage(Component.text("Island not found.", NamedTextColor.RED));
                    yield true;
                }
                long level;
                try {
                    level = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid level.", NamedTextColor.RED));
                    yield true;
                }
                service.setLevel(snap.id(), level).thenAccept(ok ->
                        sender.sendMessage(Component.text(
                                Boolean.TRUE.equals(ok) ? "Level set." : "Failed.",
                                Boolean.TRUE.equals(ok) ? NamedTextColor.GREEN : NamedTextColor.RED)));
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("/yapblock reload|tp|disband|setlevel", NamedTextColor.AQUA));
                yield true;
            }
        };
    }

    private IslandSnapshot resolveIsland(IslandServiceImpl service, String key) {
        Player online = Bukkit.getPlayerExact(key);
        if (online != null) {
            return service.islandOf(online.getUniqueId()).orElse(null);
        }
        try {
            return service.islandById(Long.parseLong(key)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "tp", "disband", "setlevel")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("tp")
                || args[0].equalsIgnoreCase("disband")
                || args[0].equalsIgnoreCase("setlevel"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
