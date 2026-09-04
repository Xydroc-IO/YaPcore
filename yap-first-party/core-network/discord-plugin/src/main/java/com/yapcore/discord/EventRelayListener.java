package com.yapcore.discord;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join / leave / death / advancement → Discord events webhook. */
public final class EventRelayListener implements Listener {

    private static final int COLOR_JOIN = 0x2ECC71;
    private static final int COLOR_LEAVE = 0x95A5A6;
    private static final int COLOR_DEATH = 0xE74C3C;
    private static final int COLOR_ADVANCEMENT = 0xF1C40F;

    private final DiscordPlugin plugin;

    public EventRelayListener(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.eventJoin()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.webhooks().sendEmbed(config.eventsWebhook(),
                "Joined", "**" + player.getName() + "** joined the server", COLOR_JOIN);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.eventLeave()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.webhooks().sendEmbed(config.eventsWebhook(),
                "Left", "**" + player.getName() + "** left the server", COLOR_LEAVE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.eventDeath()) {
            return;
        }
        Player player = event.getEntity();
        String detail;
        if (event.deathMessage() != null) {
            detail = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        } else {
            detail = player.getName() + " died";
        }
        plugin.webhooks().sendEmbed(config.eventsWebhook(), "Death", detail, COLOR_DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.eventAdvancement()) {
            return;
        }
        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null || display.isHidden()) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(display.title());
        String desc = PlainTextComponentSerializer.plainText().serialize(display.description());
        String body = "**" + event.getPlayer().getName() + "** completed **" + title + "**";
        if (!desc.isBlank()) {
            body += "\n" + desc;
        }
        plugin.webhooks().sendEmbed(config.eventsWebhook(), "Advancement", body, COLOR_ADVANCEMENT);
    }
}
