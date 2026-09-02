package com.yapcore.moderation.cmd;

import com.yapcore.moderation.ModerationAudit;
import com.yapcore.moderation.PunishmentType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

final class ModerationCmdSupport {
    private ModerationCmdSupport() {
    }

    record Actor(UUID uuid, String name) {
    }

    static Actor actor(CommandSender sender) {
        if (sender instanceof Player player) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        return new Actor(null, sender.getName());
    }

    static String join(String[] args, int start, String fallback) {
        if (start >= args.length) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    static String color(String raw) {
        return raw.replace('&', '§');
    }

    static void audit(PunishmentType type, String actor, String target, String reason, String detail) {
        ModerationAudit.fire(new ModerationAudit.Action(type, actor, target, reason, detail));
    }
}
