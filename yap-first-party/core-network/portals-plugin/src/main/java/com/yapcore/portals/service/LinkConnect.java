package com.yapcore.portals.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * BungeeCord {@code Connect} payload for YaP Link server-selector.
 * Thread: encode anywhere; {@code Player#sendPluginMessage} must run on the entity region.
 */
public final class LinkConnect {

    public static final String CHANNEL_LEGACY = "BungeeCord";
    public static final String CHANNEL_MODERN = "bungeecord:main";

    private LinkConnect() {
    }

    public static byte[] connectPayload(String targetServer) throws IOException {
        if (targetServer == null || targetServer.isBlank()) {
            throw new IllegalArgumentException("targetServer blank");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("Connect");
        out.writeUTF(targetServer.trim());
        return bytes.toByteArray();
    }

    public static String decodeConnectTarget(byte[] data) throws IOException {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data))) {
            String sub = in.readUTF();
            if (!"Connect".equals(sub)) {
                throw new IOException("not Connect: " + sub);
            }
            return in.readUTF();
        }
    }
}
