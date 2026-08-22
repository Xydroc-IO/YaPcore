package com.yapcore.moderation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class StaffNotify {

    private StaffNotify() {
    }

    public static void broadcast(String message) {
        String line = message.replace('&', '§');
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("yapmod.history") || online.hasPermission("yapmod.admin")) {
                online.sendMessage(line);
            }
        }
    }
}
