package com.yapcore.crossplay.bedrock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P4.5 — tracks which Bedrock columns a session already received and computes
 * the next Paper-backed {@code level_chunk} ring as the player moves.
 *
 * Flat columns remain opt-in via {@code -Dyapcore.bedrock.flat-chunks=true}.
 */
public final class BedrockColumnStreamer {

    private final Map<Long, SessionView> views = new ConcurrentHashMap<>();

    public record Column(int cx, int cz) {
    }

    private static final class SessionView {
        final Set<Long> sent = ConcurrentHashMap.newKeySet();
        volatile int lastCx = Integer.MIN_VALUE;
        volatile int lastCz = Integer.MIN_VALUE;
        volatile int radius = 8;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public void setRadius(long guid, int radius) {
        SessionView v = views.computeIfAbsent(guid, g -> new SessionView());
        v.radius = Math.max(2, Math.min(16, radius));
    }

    public int radius(long guid) {
        SessionView v = views.get(guid);
        return v != null ? v.radius : 8;
    }

    public void clear(long guid) {
        views.remove(guid);
    }

    public void markSent(long guid, int cx, int cz) {
        SessionView v = views.computeIfAbsent(guid, g -> new SessionView());
        v.sent.add(key(cx, cz));
    }

    public boolean wasSent(long guid, int cx, int cz) {
        SessionView v = views.get(guid);
        return v != null && v.sent.contains(key(cx, cz));
    }

    /** Forget one column so the next stream will re-send (after dig/place). */
    public void invalidate(long guid, int cx, int cz) {
        SessionView v = views.get(guid);
        if (v != null) {
            v.sent.remove(key(cx, cz));
        }
    }

    public void invalidateAllSessions(int cx, int cz) {
        long k = key(cx, cz);
        for (SessionView v : views.values()) {
            v.sent.remove(k);
        }
    }

    /**
     * Columns still needed around block position within {@code radius}.
     * Returns empty when the player has not crossed into a new chunk since last call
     * (unless {@code force}).
     */
    public List<Column> missingAround(long guid, int blockX, int blockZ, boolean force) {
        SessionView v = views.computeIfAbsent(guid, g -> new SessionView());
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        if (!force && cx == v.lastCx && cz == v.lastCz) {
            return List.of();
        }
        v.lastCx = cx;
        v.lastCz = cz;
        int r = v.radius;
        List<Column> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // Circular-ish view distance (chebyshev with corner trim)
                if (Math.max(Math.abs(dx), Math.abs(dz)) > r) {
                    continue;
                }
                int nx = cx + dx;
                int nz = cz + dz;
                if (v.sent.add(key(nx, nz))) {
                    out.add(new Column(nx, nz));
                }
            }
        }
        return out;
    }

    /**
     * Initial spawn ring — tighter than full radius so login stays light;
     * continuous {@link #missingAround} fills the rest on move.
     */
    public List<Column> initialRing(long guid, int blockX, int blockZ, int spawnRing) {
        SessionView v = views.computeIfAbsent(guid, g -> new SessionView());
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        v.lastCx = cx;
        v.lastCz = cz;
        int r = Math.max(1, Math.min(spawnRing, v.radius));
        List<Column> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int nx = cx + dx;
                int nz = cz + dz;
                if (v.sent.add(key(nx, nz))) {
                    out.add(new Column(nx, nz));
                }
            }
        }
        return out;
    }

    /** Test helper — columns currently marked sent. */
    Set<Long> sentKeys(long guid) {
        SessionView v = views.get(guid);
        return v == null ? Set.of() : new HashSet<>(v.sent);
    }
}
