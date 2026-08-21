package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.Database;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /yapdata reload|status|save
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final Database database;
    private final SyncService sync;

    public AdminCommand(JavaPlugin plugin, PlayerDataConfig config, Database database, SyncService sync) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.sync = sync;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdata.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /yapdata <reload|status|save>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                config.reload();
                sender.sendMessage("YaPPlayerData config reloaded (DB URL changes require restart).");
            }
            case "status" -> {
                sender.sendMessage("YaPPlayerData status:");
                sender.sendMessage("  server-id: " + config.serverId());
                sender.sendMessage("  inventory-profile: " + config.inventoryProfile());
                sender.sendMessage("  db open: " + database.isOpen());
                sender.sendMessage("  online ready: "
                        + plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> sync.isReady(p.getUniqueId())).count()
                        + " / " + plugin.getServer().getOnlinePlayers().size());
                sender.sendMessage("  autosave: " + config.autosaveSeconds() + "s");
                sender.sendMessage("  kits: " + config.kits().size() + " jobs: " + config.jobs().size());
            }
            case "save" -> {
                sync.saveAllOnlineBlocking();
                sender.sendMessage("Forced save of online players.");
            }
            default -> sender.sendMessage("Usage: /yapdata <reload|status|save>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("reload", "status", "save")
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        return List.of();
    }
}
