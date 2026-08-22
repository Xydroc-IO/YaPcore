package com.yapcore.essentials.listener;

import com.yapcore.essentials.store.StaffService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class SocialSpyListener implements Listener {

    private final StaffService staff;

    public SocialSpyListener(StaffService staff) {
        this.staff = staff;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String line = "§7[Spy] §f" + sender.getName() + "§7: §f" + message;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) {
                continue;
            }
            if (online.hasPermission("yapessentials.staff.socialspy")
                    && staff.isSocialSpy(online.getUniqueId())) {
                online.sendMessage(line);
            }
        }
    }
}
