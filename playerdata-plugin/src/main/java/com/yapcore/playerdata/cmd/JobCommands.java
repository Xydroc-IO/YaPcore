package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.JobRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class JobCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final JobRepository jobs;
    private final SyncService sync;
    private final com.yapcore.playerdata.gui.Menus menus;

    public JobCommands(PlayerDataConfig config, JobRepository jobs, SyncService sync,
                       com.yapcore.playerdata.gui.Menus menus) {
        this.config = config;
        this.jobs = jobs;
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
        try {
            if (args.length == 0) {
                menus.openJobs(player);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") && args.length >= 2) {
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!config.jobs().containsKey(id)) {
                    player.sendMessage("§cUnknown job.");
                    return true;
                }
                jobs.join(player.getUniqueId(), id);
                player.sendMessage("§aJoined job §f" + id);
                return true;
            }
            if (sub.equals("leave") && args.length >= 2) {
                jobs.leave(player.getUniqueId(), args[1].toLowerCase(Locale.ROOT));
                player.sendMessage("§aLeft job §f" + args[1].toLowerCase(Locale.ROOT));
                return true;
            }
            if (sub.equals("list")) {
                player.sendMessage("§aAvailable: §f" + String.join(", ", config.jobs().keySet()));
                return true;
            }
            player.sendMessage("Usage: /jobs [join|leave|list] [name]");
            return true;
        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("leave"))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : config.jobs().keySet()) {
                if (id.startsWith(p)) {
                    out.add(id);
                }
            }
            return out;
        }
        return List.of();
    }
}
