package com.yapcore.lib.packet;

/** Wire direction relative to the game server. */
public enum PacketDirection {
    /** Server → client. */
    CLIENTBOUND,
    /** Client → server. */
    SERVERBOUND
}
