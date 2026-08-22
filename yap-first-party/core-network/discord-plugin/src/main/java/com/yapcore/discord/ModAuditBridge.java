package com.yapcore.discord;

import com.yapcore.moderation.ModerationAudit;
import com.yapcore.moderation.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

public final class ModAuditBridge implements ModerationAudit.Listener {

    public static final String MOD_CHANNEL = "yap:mod";

    private final DiscordPlugin plugin;

    public ModAuditBridge(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onAction(ModerationAudit.Action action) {
        DiscordConfig config = plugin.config();
        if (config == null) {
            return;
        }
        String title = label(action.type());
        int color = color(action.type());
        String body = "**Staff:** " + action.actorName()
                + "\n**Target:** " + action.targetName()
                + "\n**Reason:** " + action.reason();
        if (action.detail() != null && !action.detail().isBlank()) {
            body += "\n**Detail:** " + action.detail();
        }
        plugin.webhooks().sendEmbed(config.moderationWebhook(), title, body, color);
        relayToLink(title, body, color);
    }

    private void relayToLink(String title, String body, int color) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        String safeTitle = title.replace('|', '/');
        String safeBody = body.replace('|', '/');
        String payload = "MOD|" + safeTitle + "|" + safeBody + "|" + color;
        carrier.sendPluginMessage(plugin, MOD_CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String label(PunishmentType type) {
        return switch (type) {
            case BAN -> "Player Banned";
            case MUTE -> "Player Muted";
            case WARN -> "Player Warned";
            case KICK -> "Player Kicked";
            default -> "Moderation Action";
        };
    }

    private static int color(PunishmentType type) {
        return switch (type) {
            case BAN, KICK -> 0xE74C3C;
            case MUTE -> 0xF39C12;
            case WARN -> 0xF1C40F;
            default -> 0x95A5A6;
        };
    }
}
