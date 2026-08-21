package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.LocationRow;
import com.yapcore.playerdata.sync.SyncService;
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

public final class HomeCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final HomesRepository homes;
    private final SyncService sync;
    private final com.yapcore.playerdata.gui.Menus menus;

    public HomeCommands(PlayerDataConfig config, HomesRepository homes, SyncService sync,
                        com.yapcore.playerdata.gui.Menus menus) {
        this.config = config;
        this.homes = homes;
        this.sync = sync;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        try {
            return switch (cmd) {
                case "sethome" -> setHome(player, args);
                case "home" -> goHome(player, args);
                case "delhome" -> delHome(player, args);
                case "homes" -> listHomes(player);
                default -> false;
            };
        } catch (Exception e) {
            player.sendMessage("§cDatabase error: " + e.getMessage());
            return true;
        }
    }

    private boolean setHome(Player player, String[] args) throws Exception {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        if (!homes.get(player.getUniqueId(), name).isPresent()
                && homes.count(player.getUniqueId()) >= config.maxHomes()) {
            player.sendMessage("§cHome limit reached (" + config.maxHomes() + ").");
            return true;
        }
        homes.upsert(player.getUniqueId(), Teleports.fromPlayer(player, name, config.serverId()));
        player.sendMessage("§aHome §f" + name + " §aset.");
        return true;
    }

    private boolean goHome(Player player, String[] args) throws Exception {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        var opt = homes.get(player.getUniqueId(), name);
        if (opt.isEmpty()) {
            player.sendMessage("§cUnknown home §f" + name);
            return true;
        }
        if (Teleports.tryTeleport(player, opt.get(), config.serverId())) {
            player.sendMessage("§aTeleported to home §f" + name);
        }
        return true;
    }

    private boolean delHome(Player player, String[] args) throws Exception {
        if (args.length < 1) {
            player.sendMessage("Usage: /delhome <name>");
            return true;
        }
        if (homes.delete(player.getUniqueId(), args[0])) {
            player.sendMessage("§aDeleted home §f" + args[0].toLowerCase(Locale.ROOT));
        } else {
            player.sendMessage("§cUnknown home.");
        }
        return true;
    }

    private boolean listHomes(Player player) throws Exception {
        List<LocationRow> list = homes.list(player.getUniqueId());
        if (list.isEmpty()) {
            player.sendMessage("§7No homes set.");
            return true;
        }
        player.sendMessage("§aHomes: §f" + list.stream().map(LocationRow::name).collect(Collectors.joining(", ")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        try {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (LocationRow h : homes.list(player.getUniqueId())) {
                if (h.name().startsWith(p)) {
                    out.add(h.name());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
