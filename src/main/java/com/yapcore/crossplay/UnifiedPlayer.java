package com.yapcore.crossplay;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientSession;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Edition-agnostic player in the shared YapEngine world.
 * Java and Bedrock sessions both map here (Geyser-class model).
 */
public final class UnifiedPlayer {

    private final UUID worldId;
    private final String username;
    private final ClientEdition edition;
    private final UUID sessionId;
    private final AtomicInteger blockX = new AtomicInteger(0);
    private final AtomicInteger blockY = new AtomicInteger(64);
    private final AtomicInteger blockZ = new AtomicInteger(0);
    private final AtomicReference<String> dimension = new AtomicReference<>("overworld");

    public UnifiedPlayer(ClientSession session) {
        Objects.requireNonNull(session);
        this.worldId = UUID.randomUUID();
        this.username = session.getUsername();
        this.edition = session.getEdition();
        this.sessionId = session.getSessionId();
    }

    public UUID getWorldId() {
        return worldId;
    }

    public String getUsername() {
        return username;
    }

    public ClientEdition getEdition() {
        return edition;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getBlockX() {
        return blockX.get();
    }

    public int getBlockY() {
        return blockY.get();
    }

    public int getBlockZ() {
        return blockZ.get();
    }

    public String getDimension() {
        return dimension.get();
    }

    public void setPosition(int x, int y, int z) {
        blockX.set(x);
        blockY.set(y);
        blockZ.set(z);
    }

    public void setDimension(String dim) {
        if (dim != null && !dim.isBlank()) {
            dimension.set(dim.trim());
        }
    }

    @Override
    public String toString() {
        return username + "[" + edition + "] @" + getDimension()
                + " (" + getBlockX() + "," + getBlockY() + "," + getBlockZ() + ")";
    }
}
