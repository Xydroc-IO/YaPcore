package com.yapcore.chat;

import com.yapcore.moderation.ModerationService;
import com.yapcore.moderation.Punishment;
import com.yapcore.perms.YaPPerms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class ChatFormat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ChatFormat() {
    }

    public static Component format(ChatConfig config, Player player, String plainMessage, String channelName) {
        ChatConfig.ChannelDef channel = config.channel(channelName);
        YaPPerms perms = Bukkit.getServicesManager().load(YaPPerms.class);
        String prefix = "";
        String suffix = "";
        if (perms != null) {
            prefix = perms.getPrefix(player.getUniqueId()).orElse("");
            suffix = perms.getSuffix(player.getUniqueId()).orElse("");
        }
        String rendered = channel.format()
                .replace("{prefix}", color(prefix))
                .replace("{suffix}", color(suffix))
                .replace("{group}", perms != null ? perms.displayGroup(player.getUniqueId()) : "")
                .replace("{player}", player.getName())
                .replace("{message}", color(plainMessage));
        return LEGACY.deserialize(color(rendered));
    }

    public static Component formatNetwork(ChatConfig config, String channelName, String serverId,
                                          String senderName, String plainMessage) {
        ChatConfig.ChannelDef channel = config.channel(channelName);
        String rendered = channel.format()
                .replace("{prefix}", "")
                .replace("{suffix}", "")
                .replace("{group}", "")
                .replace("{player}", color("&7[" + serverId + "] &f" + senderName))
                .replace("{message}", color(plainMessage));
        return LEGACY.deserialize(color(rendered));
    }

    public static Component legacy(String raw) {
        return LEGACY.deserialize(color(raw));
    }

    public static String color(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('&', '§');
    }

    public static Optional<Punishment> activeMute(UUID uuid) {
        ModerationService mod = Bukkit.getServicesManager().load(ModerationService.class);
        if (mod == null) {
            return Optional.empty();
        }
        return mod.activeMute(uuid);
    }
}
