package com.yapcore.admin.staff;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Fabric staff client channel {@code yap:staff}.
 * <ul>
 *   <li>{@code RUN|&lt;command without leading slash&gt;} — dispatch as the player
 *       (bypasses chat_command length / decode limits for long {@code /yapitems create} lines)</li>
 * </ul>
 */
public final class StaffChannel implements PluginMessageListener {

    public static final String CHANNEL = "yap:staff";
    /** Plugin-message bodies stay well under Paper's default max. */
    private static final int MAX_COMMAND_CHARS = 8_000;

    private final AdminPlugin plugin;

    public StaffChannel(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null) {
            return;
        }
        String text = decode(message);
        if (text.isEmpty() || !text.regionMatches(true, 0, "RUN|", 0, 4)) {
            return;
        }
        String command = text.substring(4).trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty() || command.length() > MAX_COMMAND_CHARS) {
            return;
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) {
            return;
        }
        final String toRun = command;
        YapSched.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!player.isOp() && !player.hasPermission("yapadmin.menu")) {
                YapMessages.noPermission(player, "yapadmin.menu");
                return;
            }
            Bukkit.dispatchCommand(player, toRun);
        });
    }

    private static String decode(byte[] message) {
        String text = new String(message == null ? new byte[0] : message, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return "";
        }
        int start = 0;
        while (start < text.length() && text.charAt(start) < 32) {
            start++;
        }
        return text.substring(start).trim();
    }
}
