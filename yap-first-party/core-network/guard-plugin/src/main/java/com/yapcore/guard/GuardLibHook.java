package com.yapcore.guard;

import com.yapcore.lib.PacketService;
import com.yapcore.lib.PacketServices;
import org.bukkit.plugin.Plugin;

/** Isolated YaPLib imports so YaPGuard still loads without yap-lib.jar. */
public final class GuardLibHook {

    private GuardLibHook() {
    }

    public static boolean install(GuardPlugin plugin) {
        PacketService packets = PacketServices.packets();
        if (packets == null) {
            return false;
        }
        packets.addListener(plugin, new GuardSpeedPackets(plugin));
        packets.addListener(plugin, new GuardCombatPackets(plugin));
        packets.addListener(plugin, new GuardScaffoldPackets(plugin));
        plugin.getLogger().info("YaPLib packet checks — speed/reach/scaffold");
        return true;
    }

    public static void uninstall(Plugin plugin) {
        PacketService packets = PacketServices.packets();
        if (packets != null) {
            packets.removeListeners(plugin);
        }
    }
}
