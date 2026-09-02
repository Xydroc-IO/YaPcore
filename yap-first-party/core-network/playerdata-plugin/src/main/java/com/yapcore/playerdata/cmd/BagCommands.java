package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.bag.BackpackPages;
import com.yapcore.playerdata.bag.BackpackService;
import com.yapcore.playerdata.db.BackpackRepository;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
public final class BagCommands implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final BackpackService backpack;
    private final BackpackRepository repository;
    private final SyncService sync;

    public BagCommands(Plugin plugin, BackpackService backpack, BackpackRepository repository, SyncService sync) {
        this.plugin = plugin;
        this.backpack = backpack;
        this.repository = repository;
        this.sync = sync;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Perms.require(sender, BackpackPages.NODE_USE)) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("see") || args[0].equalsIgnoreCase("view"))) {
            return see(player, args);
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("§e/" + label + " [page]  §7or  §e/" + label + " see <player> [page]");
                return true;
            }
        }
        backpack.openOwn(player, page);
        return true;
    }

    private boolean see(Player viewer, String[] args) {
        if (!Perms.require(viewer, BackpackPages.NODE_SEE)) {
            return true;
        }
        if (args.length < 2) {
            viewer.sendMessage("§e/bag see <player> [page]");
            return true;
        }
        String name = args[1];
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                viewer.sendMessage("§e/bag see <player> [page]");
                return true;
            }
        }
        int requested = page;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            backpack.openSee(viewer, online.getUniqueId(), online.getName(), requested);
            return true;
        }
        YapSched.async(plugin, () -> {
            Optional<BackpackRepository.Owner> owner;
            try {
                owner = repository.findOwnerByName(name);
            } catch (Exception e) {
                YapSched.entity(plugin, viewer, () -> viewer.sendMessage("§cDatabase error: " + e.getMessage()));
                return;
            }
            YapSched.entity(plugin, viewer, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (owner.isEmpty()) {
                    viewer.sendMessage("§cUnknown player §f" + name);
                    return;
                }
                BackpackRepository.Owner found = owner.get();
                backpack.openSee(viewer, found.uuid(), found.name(), requested);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("see".startsWith(p) && sender.hasPermission(BackpackPages.NODE_SEE)) {
                out.add("see");
            }
            for (int i = 1; i <= 9; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("see") && sender.hasPermission(BackpackPages.NODE_SEE)) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
