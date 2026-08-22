package com.yapcore.chat;

import com.yapcore.moderation.Punishment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Blocks most commands while muted (auth commands still allowed). */
public final class CommandMuteListener implements Listener {

    private static final Set<String> ALLOWED = Set.of(
            "/login", "/l", "/register", "/reg", "/logout", "/changepassword", "/changepass", "/cp");

    private final ChatConfig config;

    public CommandMuteListener(ChatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Optional<Punishment> mute = ChatFormat.activeMute(player.getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        String msg = event.getMessage().trim().toLowerCase(Locale.ROOT);
        for (String allowed : ALLOWED) {
            if (msg.startsWith(allowed + " ") || msg.equals(allowed)) {
                return;
            }
        }
        event.setCancelled(true);
        player.sendMessage(ChatFormat.legacy(config.mutedMessage()
                .replace("{reason}", mute.get().reason())));
    }
}
