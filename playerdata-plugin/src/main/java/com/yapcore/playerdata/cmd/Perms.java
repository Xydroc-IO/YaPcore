package com.yapcore.playerdata.cmd;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared permission checks for YaPPlayerData commands / GUIs. */
public final class Perms {
    private Perms() {
    }

    public static boolean require(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        sender.sendMessage("§cNo permission.");
        return false;
    }

    public static boolean hasKit(Player player, String kitId) {
        return player.hasPermission("yapdata.kit.*")
                || player.hasPermission("yapdata.kit." + kitId);
    }

    public static boolean hasJob(Player player, String jobId) {
        return player.hasPermission("yapdata.job.*")
                || player.hasPermission("yapdata.job." + jobId);
    }
}
