package com.yapcore.lib.packet;

/**
 * Folia-safe packet intercept callback. Default thread is the Netty event loop.
 * Use {@link PacketThreadMode#REGION} to leave the Netty thread: entity region
 * when a player is bound, global region during handshake/login. Cancel holds
 * the packet until the hop returns.
 */
public interface PacketListener {

    default PacketPriority priority() {
        return PacketPriority.NORMAL;
    }

    default PacketThreadMode threadMode() {
        return PacketThreadMode.NETTY;
    }

    default boolean listening(PacketType type, PacketDirection direction) {
        return true;
    }

    void onPacket(PacketEvent event);
}
