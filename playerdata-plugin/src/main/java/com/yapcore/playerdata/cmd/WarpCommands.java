package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.LocationRow;
import com.yapcore.playerdata.db.WarpsRepository;
import com.yapcore.playerdata.util.Teleports;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WarpCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final WarpsRepository warps;
    private final com.yapcore.playerdata.gui.Menus menus;

    public WarpCommands(PlayerDataConfig config, WarpsRepository warps,
                        com.yapcore.playerdata.gui.Menus menus) {
        this.config = config;
        this.warps = warps;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        try {
            return switch (cmd) {
                case "setwarp" -> setWarp(sender, args);
                case "delwarp" -> delWarp(sender, args);
                case "warp" -> warp(sender, args);
                case "warps" -> {
                    if (!Perms.require(sender, "yapdata.warp")) {
                        yield true;
                    }
                    if (sender instanceof Player player) {
                        menus.openWarps(player);
                    } else {
                        list(sender);
                    }
                    yield true;
                }
                default -> false;
            };
        } catch (Exception e) {
            sender.sendMessage("§cDatabase error: " + e.getMessage());
            return true;
        }
    }

    private boolean setWarp(CommandSender sender, String[] args) throws Exception {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("yapdata.warp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /setwarp <name>");
            return true;
        }
        warps.upsert(Teleports.fromPlayer(player, args[0].toLowerCase(Locale.ROOT), config.serverId()));
        sender.sendMessage("§aWarp §f" + args[0].toLowerCase(Locale.ROOT) + " §aset.");
        return true;
    }

    private boolean delWarp(CommandSender sender, String[] args) throws Exception {
        if (!sender.hasPermission("yapdata.warp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /delwarp <name>");
            return true;
        }
        if (warps.delete(args[0])) {
            sender.sendMessage("§aDeleted warp §f" + args[0].toLowerCase(Locale.ROOT));
        } else {
            sender.sendMessage("§cUnknown warp.");
        }
        return true;
    }

    private boolean warp(CommandSender sender, String[] args) throws Exception {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Perms.require(sender, "yapdata.warp")) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /warp <name>");
            return true;
        }
        var opt = warps.get(args[0]);
        if (opt.isEmpty()) {
            sender.sendMessage("§cUnknown warp.");
            return true;
        }
        if (Teleports.tryTeleport(player, opt.get(), config.serverId())) {
            player.sendMessage("§aWarped to §f" + opt.get().name());
        }
        return true;
    }

    private boolean list(CommandSender sender) throws Exception {
        List<LocationRow> list = warps.list();
        if (list.isEmpty()) {
            sender.sendMessage("§7No warps.");
            return true;
        }
        sender.sendMessage("§aWarps: §f" + list.stream()
                .map(w -> w.name() + "(" + w.serverId() + ")")
                .collect(Collectors.joining(", ")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        try {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (LocationRow w : warps.list()) {
                if (w.name().startsWith(p)) {
                    out.add(w.name());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
