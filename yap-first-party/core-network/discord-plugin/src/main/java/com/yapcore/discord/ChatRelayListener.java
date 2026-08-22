package com.yapcore.discord;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatRelayListener implements Listener {

    private final DiscordPlugin plugin;

    public ChatRelayListener(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.mcToDiscord()) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String line = "**" + event.getPlayer().getName() + ":** " + message;
        plugin.webhooks().sendPlain(config.chatWebhook(), line);
    }
}
