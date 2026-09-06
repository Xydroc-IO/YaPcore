package com.yapcore.factions.listener;

import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.service.FactionServiceImpl;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

/** Routes public chat into faction/ally channel when toggle is on. */
public final class FactionChatListener implements Listener {

    private final FactionsConfig config;
    private final FactionServiceImpl factions;

    public FactionChatListener(FactionsConfig config, FactionServiceImpl factions) {
        this.config = config;
        this.factions = factions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.perksChatChannelToggle()) {
            return;
        }
        Player player = event.getPlayer();
        FactionChatState.Channel channel = factions.chatState().channel(player.getUniqueId());
        if (channel == FactionChatState.Channel.PUBLIC) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (channel == FactionChatState.Channel.ALLY) {
            factions.sendAllyChat(player, message);
        } else {
            factions.sendFactionChat(player, message);
        }
    }
}
