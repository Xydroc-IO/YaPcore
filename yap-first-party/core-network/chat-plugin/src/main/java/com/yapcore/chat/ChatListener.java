package com.yapcore.chat;

import com.yapcore.chat.service.ChatFilterService;
import com.yapcore.chat.service.ChatServiceImpl;
import com.yapcore.chat.service.IgnoreService;
import com.yapcore.chat.service.PlayerChannelService;
import com.yapcore.chat.service.SlowModeService;
import com.yapcore.moderation.Punishment;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

public final class ChatListener implements Listener {

    private final ChatConfig config;
    private final SlowModeService slowMode;
    private final ChatFilterService filter;
    private final PlayerChannelService channels;
    private final IgnoreService ignore;
    private final ChatServiceImpl chatService;

    public ChatListener(ChatConfig config, SlowModeService slowMode, ChatFilterService filter,
                        PlayerChannelService channels, IgnoreService ignore,
                        ChatServiceImpl chatService) {
        this.config = config;
        this.slowMode = slowMode;
        this.filter = filter;
        this.channels = channels;
        this.ignore = ignore;
        this.chatService = chatService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player source = event.getPlayer();
        if (!source.hasPermission("yapchat.use")) {
            event.setCancelled(true);
            source.sendMessage(ChatFormat.legacy(config.filteredMessage()));
            return;
        }

        Optional<Punishment> mute = ChatFormat.activeMute(source.getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            source.sendMessage(ChatFormat.legacy(config.mutedMessage()
                    .replace("{reason}", mute.get().reason())));
            return;
        }

        if (!slowMode.allow(source, config.slowModeSeconds())) {
            event.setCancelled(true);
            source.sendMessage(ChatFormat.legacy(config.slowModeMessage()
                    .replace("{seconds}", String.valueOf(slowMode.remainingSeconds(source, config.slowModeSeconds())))));
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String channel = channels.channel(source, config.defaultChannel());
        String messageText = plain;

        if (plain.startsWith(config.localPrefix()) && plain.length() > config.localPrefix().length()) {
            channel = "local";
            messageText = plain.substring(config.localPrefix().length()).trim();
        }

        if ("staff".equals(channel) && !source.hasPermission("yapchat.staff")) {
            event.setCancelled(true);
            source.sendMessage(ChatFormat.legacy("&cNo permission for staff channel."));
            return;
        }

        if (!source.hasPermission("yapchat.bypass.filter")) {
            ChatFilterService.FilterResult filtered = filter.filter(messageText);
            if (filtered.blocked()) {
                event.setCancelled(true);
                source.sendMessage(ChatFormat.legacy(config.filteredMessage()));
                return;
            }
            if (filtered.matched()) {
                messageText = filtered.message();
            }
        }

        ChatConfig.ChannelDef channelDef = config.channel(channel);
        String finalPlain = messageText;
        Component rendered = ChatFormat.format(config, source, finalPlain, channel);
        event.setCancelled(true);

        if (config.unsignedSystemChat()) {
            broadcastUnsigned(event, source, rendered, channelDef.radius());
        } else {
            broadcastSigned(event, source, rendered, channelDef.radius(), channel);
        }
        chatService.forwardLocalChat(channel, source.getUniqueId(), source.getName(), finalPlain);
    }

    private void broadcastUnsigned(AsyncChatEvent event, Player source, Component rendered, int radius) {
        for (Audience viewer : event.viewers()) {
            if (viewer instanceof Player target) {
                if (ignore.isIgnoring(target, source)) {
                    continue;
                }
                if (radius > 0 && (source.getWorld() != target.getWorld()
                        || source.getLocation().distanceSquared(target.getLocation()) > radius * radius)) {
                    continue;
                }
            }
            viewer.sendMessage(rendered);
        }
    }

    private void broadcastSigned(AsyncChatEvent event, Player source, Component rendered, int radius, String channel) {
        for (Audience viewer : event.viewers()) {
            if (viewer instanceof Player target) {
                if (ignore.isIgnoring(target, source)) {
                    continue;
                }
                if (radius > 0 && (source.getWorld() != target.getWorld()
                        || source.getLocation().distanceSquared(target.getLocation()) > radius * radius)) {
                    continue;
                }
                if ("staff".equals(channel) && !target.hasPermission("yapchat.staff")) {
                    continue;
                }
            }
            viewer.sendMessage(rendered);
        }
    }
}
