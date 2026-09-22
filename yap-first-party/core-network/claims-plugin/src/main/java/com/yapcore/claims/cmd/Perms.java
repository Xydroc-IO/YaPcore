package com.yapcore.claims.cmd;

import com.yapcore.messages.YapMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared permission checks for YaPClaims commands / GUIs. */
public final class Perms {
    private Perms() {
    }

    public static boolean require(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        YapMessages.noPermission(sender, node);
        return false;
    }
}
