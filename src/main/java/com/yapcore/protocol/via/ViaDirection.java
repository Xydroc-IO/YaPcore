package com.yapcore.protocol.via;

/** Packet flow direction relative to the Via proxy. */
public enum ViaDirection {
    /** Client → server (inbound on public edge). */
    CLIENTBOUND_TO_SERVER,
    /** Server → client (outbound to player). */
    SERVERBOUND_TO_CLIENT
}
