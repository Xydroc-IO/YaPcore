package com.yapcore.chat;

import com.yapcore.moderation.ModerationService;
import com.yapcore.moderation.Punishment;
import com.yapcore.perms.YaPPerms;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class ChatFormat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .build();

    private ChatFormat() {
    }

    public static Component format(ChatConfig config, Player player, String plainMessage, String channelName) {
        ChatConfig.ChannelDef channel = config.channel(channelName);
        YaPPerms perms = Bukkit.getServicesManager().load(YaPPerms.class);
        String prefix = "";
        String suffix = "";
        String nameColor = "&f";
        String chatColor = "&f";
        if (perms != null) {
            prefix = perms.getPrefix(player.getUniqueId()).orElse("");
            suffix = perms.getSuffix(player.getUniqueId()).orElse("");
            nameColor = perms.getNameColor(player.getUniqueId()).orElse("&f");
            chatColor = perms.getChatColor(player.getUniqueId()).orElse("&f");
        }
        String rendered = channel.format()
                .replace("{prefix}", color(prefix))
                .replace("{suffix}", color(suffix))
                .replace("{namecolor}", color(nameColor))
                .replace("{name-color}", color(nameColor))
                .replace("{chatcolor}", color(chatColor))
                .replace("{chat-color}", color(chatColor))
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
                .replace("{namecolor}", "")
                .replace("{name-color}", "")
                .replace("{chatcolor}", "&f")
                .replace("{chat-color}", "&f")
                .replace("{group}", "")
                .replace("{player}", color("&7[" + serverId + "] &f" + senderName))
                .replace("{message}", color(plainMessage));
        return LEGACY.deserialize(color(rendered));
    }

    public static Component legacy(String raw) {
        return LEGACY.deserialize(color(raw));
    }

    /**
     * YaP-Folia {@code Player.sendMessage(Component)} is a {@code ClientboundSystemChatPacket}
     * (unsigned). Do not use {@code sendMessage(Identity, Component)} — Adventure maps that
     * to {@code MessageType.CHAT}, which clients flag as "Chat messages cannot be verified".
     */
    public static void sendSystem(Audience audience, Component message) {
        if (audience == null || message == null) {
            return;
        }
        if (audience instanceof Player player) {
            player.sendMessage(message);
            return;
        }
        audience.sendMessage(message);
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
