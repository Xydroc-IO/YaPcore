package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitCommands implements CommandExecutor, TabCompleter {
    private final PlayerDataConfig config;
    private final KitRepository kits;
    private final SyncService sync;
    private final com.yapcore.playerdata.gui.Menus menus;

    public KitCommands(PlayerDataConfig config, KitRepository kits, SyncService sync,
                       com.yapcore.playerdata.gui.Menus menus) {
        this.config = config;
        this.kits = kits;
        this.sync = sync;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Perms.require(sender, "yapdata.kit")) {
            return true;
        }
        if (!sync.isReady(player.getUniqueId())) {
            player.sendMessage("§cStill loading your data…");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        try {
            if (cmd.equals("kits")) {
                menus.openKits(player);
                return true;
            }
            if (args.length < 1) {
                menus.openKits(player);
                return true;
            }
            String id = args[0].toLowerCase(Locale.ROOT);
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
        } catch (Exception e) {
            player.sendMessage("§cDatabase error: " + e.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || command.getName().equalsIgnoreCase("kits")) {
            return List.of();
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String p = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String id : config.kits().keySet()) {
            if (id.startsWith(p) && Perms.hasKit(player, id)) {
                out.add(id);
            }
        }
        return out;
    }
}
