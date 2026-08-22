package com.yapcore.perms.engine;

import com.yapcore.perms.EffectiveUser;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionApplicator {

    private final JavaPlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionApplicator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply(Player player, EffectiveUser effective) {
        PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(),
                uuid -> player.addAttachment(plugin));
        for (String node : attachment.getPermissions().keySet()) {
            attachment.unsetPermission(node);
        }
        for (Map.Entry<String, Boolean> entry : effective.permissions().entrySet()) {
            attachment.setPermission(entry.getKey(), entry.getValue());
        }
        player.recalculatePermissions();
    }

    public void detach(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
            player.recalculatePermissions();
        }
    }

    public void detachAll() {
        for (UUID uuid : attachments.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                detach(player);
            }
        }
        attachments.clear();
    }
}
