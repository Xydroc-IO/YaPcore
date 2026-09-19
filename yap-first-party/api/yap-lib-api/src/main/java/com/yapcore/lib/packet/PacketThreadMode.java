package com.yapcore.lib.packet;

/**
 * Where {@link PacketListener#onPacket} runs.
 * <p>
 * {@link #NETTY} is the connection event loop. Cancel/rewrite here without
 * touching world/entity state.
 * {@link #REGION} hops off the event loop: the player's entity scheduler when
 * Bukkit has bound a player, otherwise Folia's global region scheduler
 * (handshake/login). If the listener can cancel, the packet is <em>held</em>
 * before {@code packet_handler} until that hop returns — Netty is never
 * blocked. {@link PacketPriority#MONITOR} region listeners stay observe-only
 * and do not delay the packet.
 */
public enum PacketThreadMode {
    NETTY,
    REGION
}
