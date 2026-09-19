package com.yapcore.lib.packet;

import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Fired while a packet is still owned by the intercept pipeline.
 * Cancel and rewrite are valid on {@link PacketThreadMode#NETTY} and on
 * {@link PacketThreadMode#REGION} when the packet is held (not yet delivered
 * to {@code packet_handler}). {@link PacketPriority#MONITOR} cannot cancel.
 * Observe-only copies ({@link #asObserveOnly()}) ignore cancel/rewrite.
 */
public final class PacketEvent {

    private final Player player;
    private final UUID playerId;
    private final PacketDirection direction;
    private PacketContainer packet;
    private final boolean nettyThread;
    private final InetSocketAddress address;
    private boolean cancelled;
    private PacketPriority currentPriority = PacketPriority.NORMAL;
    private boolean observeOnly;

    public PacketEvent(Player player, UUID playerId, PacketDirection direction,
                       PacketContainer packet, boolean nettyThread) {
        this(player, playerId, direction, packet, nettyThread, null);
    }

    public PacketEvent(Player player, UUID playerId, PacketDirection direction,
                       PacketContainer packet, boolean nettyThread, InetSocketAddress address) {
        this.player = player;
        this.playerId = playerId;
        this.direction = direction;
        this.packet = packet;
        this.nettyThread = nettyThread;
        this.address = address;
    }

    /** May be null during handshake/login before Bukkit binds the player. */
    public Player player() {
        return player;
    }

    public UUID playerId() {
        return playerId;
    }

    /** Remote address; set from the connection even during handshake/login. */
    public InetSocketAddress address() {
        return address;
    }

    public PacketDirection direction() {
        return direction;
    }

    public PacketType type() {
        return packet.type();
    }

    public PacketContainer packet() {
        return packet;
    }

    public void setPacket(PacketContainer packet) {
        if (observeOnly) {
            return;
        }
        this.packet = packet;
    }

    public void setHandle(Object nmsPacket) {
        if (observeOnly) {
            return;
        }
        packet.setHandle(nmsPacket);
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        if (observeOnly) {
            return;
        }
        if (cancelled && currentPriority != null && !currentPriority.canCancel()) {
            return;
        }
        this.cancelled = cancelled;
    }

    public boolean nettyThread() {
        return nettyThread;
    }

    public boolean observeOnly() {
        return observeOnly;
    }

    public PacketEvent withPriority(PacketPriority priority) {
        this.currentPriority = priority;
        return this;
    }

    public PacketEvent asObserveOnly() {
        PacketEvent copy = new PacketEvent(player, playerId, direction, packet, false, address);
        copy.cancelled = cancelled;
        copy.observeOnly = true;
        copy.currentPriority = PacketPriority.MONITOR;
        return copy;
    }
}
