package com.yapcore.playerdata.bag;

import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Fabric client channel {@code yap:bag}.
 * <ul>
 *   <li>{@code HELLO} — client has page tabs; omit chest-item nav for that player</li>
 *   <li>{@code OPEN} / {@code OPEN|&lt;page&gt;} — open bag (same as {@code /bag})</li>
 * </ul>
 * Vanilla / Bedrock never send this; they use {@code /bag} and get the bottom nav row.
 */
public final class BackpackChannel implements PluginMessageListener {

    private final Plugin plugin;
    private final BackpackService backpack;

    public BackpackChannel(Plugin plugin, BackpackService backpack) {
        this.plugin = plugin;
        this.backpack = backpack;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BackpackService.CHANNEL.equals(channel) || player == null) {
            return;
        }
        String text = decode(message);
        if (text.isEmpty()) {
            return;
        }
        if (text.regionMatches(true, 0, "HELLO", 0, 5)) {
            backpack.markBagClient(player.getUniqueId());
            return;
        }
        if (!text.regionMatches(true, 0, "OPEN", 0, 4)) {
            return;
        }
        int page = 1;
        int pipe = text.indexOf('|');
        if (pipe >= 0 && pipe + 1 < text.length()) {
            try {
                page = Integer.parseInt(text.substring(pipe + 1).trim());
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        int requested = page;
        YapSched.entity(plugin, player, () -> {
            if (!player.isOnline() || !Perms.require(player, BackpackPages.NODE_USE)) {
                return;
            }
            backpack.markBagClient(player.getUniqueId());
            backpack.openOwn(player, requested);
        });
    }

    private static String decode(byte[] message) {
        String text = new String(message == null ? new byte[0] : message, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return "";
        }
        // Older / framed clients may prefix a VarInt length; strip non-text lead bytes.
        int start = 0;
        while (start < text.length() && text.charAt(start) < 32) {
            start++;
        }
        return text.substring(start).trim();
    }
}
