package com.yapcore.lib;

import com.yapcore.lib.packet.PacketContainer;
import com.yapcore.lib.packet.PacketListener;
import com.yapcore.lib.packet.PacketType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * ProtocolLib-class packet intercept. Provided by {@code YaPLib} via ServicesManager.
 * <p>
 * Listeners on {@link com.yapcore.lib.packet.PacketThreadMode#NETTY} run on the
 * connection event loop and may cancel/rewrite. Do not mutate world state there.
 */
public interface PacketService {

    void addListener(Plugin plugin, PacketListener listener);

    void removeListener(PacketListener listener);

    void removeListeners(Plugin plugin);

    int listenerCount();

    PacketContainer createPacket(PacketType type);

    Class<?> nmsClass(PacketType type);

    PacketType typeOf(Object nmsPacket);

    PacketContainer wrap(Object nmsPacket);

    /**
     * Write a packet to the player. {@code filters} true runs outbound listeners first.
     */
    void send(Player player, PacketContainer packet, boolean filters);

    void send(Player player, Object nmsPacket, boolean filters);

    /**
     * Inject a client packet as if the player sent it. {@code filters} true runs inbound listeners.
     */
    void receive(Player player, PacketContainer packet, boolean filters);

    void receive(Player player, Object nmsPacket, boolean filters);

    void broadcast(PacketContainer packet, boolean filters, Collection<? extends Player> viewers);

    boolean hasChannel(Player player);
}
