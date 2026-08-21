package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.gui.Menus;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /menu — fancy hub GUI. */
public final class MenuCommand implements CommandExecutor, TabCompleter {

    private final Menus menus;
    private final SyncService sync;

    public MenuCommand(Menus menus, SyncService sync) {
        this.menus = menus;
        this.sync = sync;
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
        menus.openHub(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
