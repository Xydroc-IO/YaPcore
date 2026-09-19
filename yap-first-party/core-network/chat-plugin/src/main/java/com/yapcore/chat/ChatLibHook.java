package com.yapcore.chat;

import com.yapcore.lib.PacketService;
import com.yapcore.lib.PacketServices;
import org.bukkit.plugin.Plugin;

/** Isolated YaPLib imports so YaPChat still loads without yap-lib.jar. */
public final class ChatLibHook {

    private ChatLibHook() {
    }

    public static boolean install(ChatPlugin plugin) {
        PacketService packets = PacketServices.packets();
        if (packets == null) {
            return false;
        }
        packets.addListener(plugin, new ChatPacketFilter(plugin));
        if (plugin.chatConfig().unsignedSystemChat()) {
            packets.addListener(plugin, new SecureChatPackets(plugin));
        }
        plugin.getLogger().info("YaPLib packet chat filter" + (plugin.chatConfig().unsignedSystemChat()
                ? " + unsigned rewrite" : ""));
        return true;
    }

    public static void uninstall(Plugin plugin) {
        PacketService packets = PacketServices.packets();
        if (packets != null) {
            packets.removeListeners(plugin);
        }
    }
}
