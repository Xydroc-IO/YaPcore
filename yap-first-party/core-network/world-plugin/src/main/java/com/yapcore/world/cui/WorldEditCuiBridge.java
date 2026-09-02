package com.yapcore.world.cui;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * WorldEditCUI protocol (legacy channel). Sends selection outlines to CUI clients.
 */
public final class WorldEditCuiBridge implements PluginMessageListener {

    public static final String CHANNEL = "worldedit:cui";
    public static final String LEGACY = "WECUI";

    private final Plugin plugin;
    private final SelectionServiceImpl selection;
    private final SelectionShape shapes;
    private boolean enabled = true;

    public WorldEditCuiBridge(Plugin plugin, SelectionServiceImpl selection, SelectionShape shapes) {
        this.plugin = plugin;
        this.selection = selection;
        this.shapes = shapes;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, LEGACY);
        } catch (Exception ignored) {
        }
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void update(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        if (!player.hasPermission("yapworld.selection") && !player.hasPermission("yapworld.cui")) {
            return;
        }
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        if (sel.isEmpty()) {
            send(player, "s|cuboid");
            return;
        }
        CuboidSelection s = sel.get();
        SelectionShape.Mode mode = shapes.mode(player.getUniqueId());
        String shape = switch (mode) {
            case SPHERE -> "ellipsoid";
            case CYL -> "cylinder";
            case POLY -> "polygon2d";
            default -> "cuboid";
        };
        send(player, "s|" + shape);
        if (mode == SelectionShape.Mode.SPHERE || mode == SelectionShape.Mode.CYL) {
            double cx = (s.minX() + s.maxX()) / 2.0;
            double cy = (s.minY() + s.maxY()) / 2.0;
            double cz = (s.minZ() + s.maxZ()) / 2.0;
            double rx = (s.maxX() - s.minX()) / 2.0 + 0.5;
            double ry = (s.maxY() - s.minY()) / 2.0 + 0.5;
            double rz = (s.maxZ() - s.minZ()) / 2.0 + 0.5;
            send(player, String.format(Locale.ROOT, "p|0|%.2f|%.2f|%.2f|0", cx, cy, cz));
            send(player, String.format(Locale.ROOT, "p|1|%.2f|%.2f|%.2f|0", rx, ry, rz));
        } else {
            send(player, "p|0|" + s.minX() + "|" + s.minY() + "|" + s.minZ() + "|0");
            send(player, "p|" + (mode == SelectionShape.Mode.POLY ? "1" : "1")
                    + "|" + s.maxX() + "|" + s.maxY() + "|" + s.maxZ() + "|0");
        }
    }

    public void update(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            update(p);
        }
    }

    private void send(Player player, String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        player.sendPluginMessage(plugin, CHANNEL, data);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Client handshake — reply with current selection
        if (CHANNEL.equals(channel) || LEGACY.equals(channel)) {
            update(player);
        }
    }
}
