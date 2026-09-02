package com.yapcore.perms.engine;

import java.time.Instant;

/** One stored permission row (user or group) with optional context and expiry. */
public record StoredNode(String node, boolean value, String world, String server, Instant expiresAt) {

    public StoredNode {
        node = node == null ? "" : node;
        world = world == null ? "" : world;
        server = server == null ? "" : server;
    }

    public boolean applies(Instant now, String currentWorld, String currentServer) {
        if (expired(now)) {
            return false;
        }
        if (!world.isBlank() && !world.equalsIgnoreCase(empty(currentWorld))) {
            return false;
        }
        if (!server.isBlank() && !server.equalsIgnoreCase(empty(currentServer))) {
            return false;
        }
        return true;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean temporary() {
        return expiresAt != null;
    }

    private static String empty(String raw) {
        return raw == null ? "" : raw;
    }
}
