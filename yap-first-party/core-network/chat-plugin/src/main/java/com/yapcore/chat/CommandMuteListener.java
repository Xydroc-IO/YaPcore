package com.yapcore.chat;

import com.yapcore.moderation.Punishment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

/** Blocks most commands while muted (auth commands still allowed). */
public final class CommandMuteListener implements Listener {

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
        if (ChatPacketText.commandAllowedWhenMuted(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        config.messages().sendRaw(player, config.mutedMessage(), "reason", mute.get().reason());
    }
}
