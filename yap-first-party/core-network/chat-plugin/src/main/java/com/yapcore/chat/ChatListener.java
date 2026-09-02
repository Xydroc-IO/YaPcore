package com.yapcore.chat;

import com.yapcore.chat.service.ChatFilterService;
import com.yapcore.chat.service.ChatServiceImpl;
import com.yapcore.chat.service.IgnoreService;
import com.yapcore.chat.service.PlayerChannelService;
import com.yapcore.chat.service.SlowModeService;
import com.yapcore.moderation.Punishment;
import com.yapcore.sched.StaffBypass;
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
            event.viewers().clear();
            source.sendMessage(ChatFormat.legacy(config.filteredMessage()));
            return;
        }

        Optional<Punishment> mute = ChatFormat.activeMute(source.getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            event.viewers().clear();
            source.sendMessage(ChatFormat.legacy(config.mutedMessage()
                    .replace("{reason}", mute.get().reason())));
            return;
        }

        if (!slowMode.allow(source, config.slowModeSeconds())) {
            event.setCancelled(true);
            event.viewers().clear();
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

        if (!config.channels().containsKey(channel) && !"local".equals(channel)) {
            channel = config.defaultChannel();
        }

        if (!config.canUseChannel(source, channel)) {
            event.setCancelled(true);
            event.viewers().clear();
            source.sendMessage(ChatFormat.legacy("&cNo permission for &f" + channel + " &cchannel."));
            channels.setChannel(source, config.defaultChannel());
            return;
        }

        if (!StaffBypass.chat(source) && !source.hasPermission("yapchat.bypass.filter")) {
            ChatFilterService.FilterResult filtered = filter.filter(messageText);
            if (filtered.blocked()) {
                event.setCancelled(true);
                event.viewers().clear();
                source.sendMessage(ChatFormat.legacy(config.filteredMessage()));
                return;
            }
            if (filtered.matched()) {
                messageText = filtered.message();
            }
        }

        ChatConfig.ChannelDef channelDef = config.channel(channel);
        String finalPlain = messageText;
        String finalChannel = channel;
        Component rendered = ChatFormat.format(config, source, finalPlain, finalChannel);
        event.setCancelled(true);
        event.viewers().clear();

        broadcast(source, rendered, channelDef, finalChannel);
        chatService.forwardLocalChat(finalChannel, source.getUniqueId(), source.getName(), finalPlain);
    }

    private void broadcast(Player source, Component rendered,
                           ChatConfig.ChannelDef channelDef, String channel) {
        int radius = channelDef.radius();
        for (Player target : source.getServer().getOnlinePlayers()) {
            if (!canSee(source, target, channelDef, channel, radius)) {
                continue;
            }
            send(target, rendered);
        }
        send(source.getServer().getConsoleSender(), rendered);
    }

    private boolean canSee(Player source, Audience viewer, ChatConfig.ChannelDef channelDef,
                           String channel, int radius) {
        if (!(viewer instanceof Player target)) {
            return true;
        }
        if (ignore.isIgnoring(target, source)) {
            return false;
        }
        if (!config.canUseChannel(target, channel)) {
            return false;
        }
        return radius <= 0 || (source.getWorld() == target.getWorld()
                && source.getLocation().distanceSquared(target.getLocation()) <= (long) radius * radius);
    }

    private void send(Audience viewer, Component rendered) {
        if (config.unsignedSystemChat()) {
            ChatFormat.sendSystem(viewer, rendered);
        } else {
            viewer.sendMessage(rendered);
        }
    }
}
