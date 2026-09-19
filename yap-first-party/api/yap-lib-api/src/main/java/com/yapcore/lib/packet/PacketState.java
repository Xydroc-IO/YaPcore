package com.yapcore.lib.packet;

/** Minecraft connection protocol state. */
public enum PacketState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY,
    COMMON,
    ANY
}
