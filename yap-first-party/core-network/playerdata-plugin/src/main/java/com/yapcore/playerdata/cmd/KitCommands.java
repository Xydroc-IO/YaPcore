package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.kit.KitGrantService;
import com.yapcore.playerdata.sync.SyncService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Survival kits (YaPPlayerData — not Essentials).
 * Players: {@code /kit [name]} · {@code /kits}
 * Console/store: {@code kit give|grant <player> <kit>}
 */
public final class KitCommands implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final KitRepository kits;
    private final SyncService sync;
    private final KitGrantService grants;
    private final com.yapcore.playerdata.gui.Menus menus;

    public KitCommands(JavaPlugin plugin, PlayerDataConfig config, KitRepository kits, SyncService sync,
                       KitGrantService grants, com.yapcore.playerdata.gui.Menus menus) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.sync = sync;
        this.grants = grants;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("kits")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only — use kit list / kit give from console.");
                return true;
            }
            if (!Perms.require(sender, "yapdata.kit")) {
                return true;
            }
            menus.openKits(player);
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("grant") || sub.equals("list") || sub.equals("help")) {
                return adminOp(sender, sub, args);
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eConsole: kit give|grant <player> <kit>  ·  kit list");
            sender.sendMessage("§7Tebex: see docs/ops/TEBEX.md");
            return true;
        }
        if (!Perms.require(sender, "yapdata.kit")) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        try {
            if (args.length < 1) {
                menus.openKits(player);
                return true;
            }
            return claim(player, args[0].toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            player.sendMessage("§cDatabase error: " + e.getMessage());
            return true;
        }
    }

    private boolean adminOp(CommandSender sender, String sub, String[] args) {
        if (sub.equals("help")) {
            sender.sendMessage("§6Kits §7(playerdata — not essentials)");
            sender.sendMessage("§e/kit [name] §7· §e/kits §7GUI");
            sender.sendMessage("§e/kit give <player> <kit> [-force] §7— online only");
            sender.sendMessage("§e/kit grant <player> <kit> §7— queue (any backend delivers)");
            sender.sendMessage("§e/kit list");
            return true;
        }
        if (sub.equals("list")) {
            if (!sender.hasPermission("yapdata.kit.give") && !sender.hasPermission("yapdata.admin")
                    && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (config.kits().isEmpty()) {
                sender.sendMessage("§cNo kits — copy kits.yml to this backend.");
                return true;
            }
            sender.sendMessage("§aKits on this backend: §f" + String.join(", ", config.kits().keySet()));
            return true;
        }
        if (!sender.hasPermission("yapdata.kit.give") && !sender.hasPermission("yapdata.admin")
                && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/kit " + sub + " <player> <kit>");
            return true;
        }
        String playerName = args[1];
        String kitId = args[2].toLowerCase(Locale.ROOT);
        boolean force = args.length >= 4 && args[3].equalsIgnoreCase("-force");
        PlayerDataConfig.KitDef def = config.kits().get(kitId);
        if (def == null) {
            sender.sendMessage("§cUnknown kit on this backend. Sync kits.yml. Known: "
                    + String.join(", ", config.kits().keySet()));
            return true;
        }

        if (sub.equals("give")) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online == null) {
                sender.sendMessage("§cPlayer not online here — use §fkit grant " + playerName + " " + kitId
                        + " §c(queues for any backend).");
                return true;
            }
            grants.giveOnline(online, def, !force).thenAccept(ok ->
                    YapSched.global(plugin, () -> {
                        if (ok) {
                            sender.sendMessage("§aGave kit §f" + kitId + " §ato §f" + online.getName());
                            online.sendMessage("§aYou received kit §f" + kitId);
                        } else {
                            sender.sendMessage("§cGive failed.");
                        }
                    }));
            return true;
        }

        // grant — queue in shared DB (works offline; delivered wherever kits.yml exists)
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline.getUniqueId();
        if (uuid == null) {
            sender.sendMessage("§cCould not resolve player.");
            return true;
        }
        Player online = offline.isOnline() ? offline.getPlayer() : Bukkit.getPlayer(uuid);
        YapSched.async(plugin, () -> {
            try {
                long id = grants.enqueue(uuid, kitId);
                if (online != null && online.isOnline() && sync.isReady(uuid)) {
                    int n = grants.deliverPending(online);
                    YapSched.global(plugin, () -> sender.sendMessage(
                            "§aGranted kit §f" + kitId + " §ato §f" + playerName
                                    + " §7(delivered now, id=" + id + ", n=" + n + ")"));
                } else {
                    YapSched.global(plugin, () -> sender.sendMessage(
                            "§aQueued kit §f" + kitId + " §afor §f" + playerName
                                    + " §7(id=" + id + ") — delivers on next join to any backend with kits.yml"));
                }
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cGrant failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean claim(Player player, String id) throws Exception {
        PlayerDataConfig.KitDef def = config.kits().get(id);
        if (def == null) {
            player.sendMessage("§cUnknown kit.");
            return true;
        }
        if (!Perms.hasKit(player, id)) {
            player.sendMessage("§cNo permission for that kit.");
            return true;
        }
        var last = kits.lastClaim(player.getUniqueId(), id);
        if (last.isPresent() && def.delaySeconds() > 0) {
            Instant next = last.get().plusSeconds(def.delaySeconds());
            if (Instant.now().isBefore(next)) {
                long secs = Duration.between(Instant.now(), next).getSeconds();
                player.sendMessage("§cKit on cooldown (" + secs + "s left).");
                return true;
            }
        }
        for (ItemStack stack : def.items()) {
            player.getInventory().addItem(stack.clone());
        }
        kits.markClaimed(player.getUniqueId(), id);
        player.sendMessage("§aClaimed kit §f" + id);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kits")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("give", "grant", "list", "help")) {
                if (s.startsWith(p) && (sender.hasPermission("yapdata.kit.give")
                        || sender.hasPermission("yapdata.admin")
                        || !(sender instanceof Player))) {
                    out.add(s);
                }
            }
            if (sender instanceof Player player) {
                for (String id : config.kits().keySet()) {
                    if (id.startsWith(p) && Perms.hasKit(player, id)) {
                        out.add(id);
                    }
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("grant"))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(pl.getName());
                }
            }
            return out;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("grant"))) {
            String p = args[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : config.kits().keySet()) {
                if (id.startsWith(p)) {
                    out.add(id);
                }
            }
            return out;
        }
        return List.of();
    }
}
