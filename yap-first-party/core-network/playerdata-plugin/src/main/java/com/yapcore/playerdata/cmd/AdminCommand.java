package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.auth.AuthService;
import com.yapcore.playerdata.db.Database;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /yapdata reload|status|save|unlock
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final Database database;
    private final SyncService sync;
    private final AuthService auth;

    public AdminCommand(JavaPlugin plugin, PlayerDataConfig config, Database database,
                        SyncService sync, AuthService auth) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.sync = sync;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapdata.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /yapdata <reload|status|save|unlock>");
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
                sender.sendMessage("  auth active: " + auth.isActive()
                        + " (enabled=" + config.authEnabled()
                        + " force=" + config.authForce()
                        + " trust-velocity=" + config.authTrustVelocity() + ")");
                sender.sendMessage("  db open: " + database.isOpen()
                        + (database.usingSharedPool() ? " (shared YaPDB)" : " (embedded)"));
                sender.sendMessage("  online ready: "
                        + plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> sync.isReady(p.getUniqueId())).count()
                        + " / " + plugin.getServer().getOnlinePlayers().size());
                sender.sendMessage("  autosave: " + config.autosaveSeconds() + "s");
                sender.sendMessage("  kits: " + config.kits().size() + " jobs: " + config.jobs().size());
                sender.sendMessage("  backpack: " + (config.featureBackpack() ? "on" : "off")
                        + " pages=" + config.backpackDefaultPages() + "-" + config.backpackMaxPages());
            }
            case "save" -> {
                sync.saveAllOnlineBlocking();
                sender.sendMessage("Forced save of online players.");
            }
            case "unlock" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /yapdata unlock <player|uuid>");
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage("§cUnknown player.");
                    return true;
                }
                try {
                    sync.sessionLock().forceRelease(uuid);
                    sender.sendMessage("§aCleared session lock for " + args[1]);
                } catch (Exception e) {
                    sender.sendMessage("§cFailed: " + e.getMessage());
                }
            }
            default -> sender.sendMessage("Usage: /yapdata <reload|status|save|unlock>");
        }
        return true;
    }

    private UUID resolveUuid(String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
        }
        Player p = Bukkit.getPlayerExact(arg);
        return p != null ? p.getUniqueId() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("reload", "status", "save", "unlock")
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unlock")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .toList();
        }
        return List.of();
    }
}
