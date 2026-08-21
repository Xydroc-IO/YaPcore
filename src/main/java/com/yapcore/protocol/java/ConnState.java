package com.yapcore.protocol.java;

/** Connection state for the Minecraft Java Edition pipeline. */
public enum ConnState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIG,
    PLAY
}
