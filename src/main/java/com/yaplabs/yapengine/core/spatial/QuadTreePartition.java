package com.yaplabs.yapengine.core.spatial;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Quad-tree partition keyed by packed chunk longs (bitwise) — no string lookups.
 */
public final class QuadTreePartition {

    private final ConcurrentHashMap<Long, SpatialQuadrant> chunkOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SpatialQuadrant, ConcurrentLinkedQueue<String>> entitiesByQuad =
            new ConcurrentHashMap<>();

    public QuadTreePartition() {
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            entitiesByQuad.put(q, new ConcurrentLinkedQueue<>());
        }
    }

    public SpatialQuadrant locate(int blockX, int blockZ) {
        long packed = BitwiseQuadrantIndex.packBlock(blockX, blockZ);
        return chunkOwners.computeIfAbsent(packed, BitwiseQuadrantIndex::fromPackedChunk);
    }

    public SpatialQuadrant locatePackedChunk(long packedChunk) {
        return chunkOwners.computeIfAbsent(packedChunk, BitwiseQuadrantIndex::fromPackedChunk);
    }

    public void registerEntity(String entityId, int blockX, int blockZ) {
        Objects.requireNonNull(entityId, "entityId");
        SpatialQuadrant q = locate(blockX, blockZ);
        for (SpatialQuadrant other : SpatialQuadrant.values()) {
            entitiesByQuad.get(other).remove(entityId);
        }
        entitiesByQuad.get(q).offer(entityId);
    }

    public void unregisterEntity(String entityId) {
        if (entityId == null) {
            return;
        }
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            entitiesByQuad.get(q).remove(entityId);
        }
    }

    public SpatialQuadrant quadrantOfEntity(String entityId) {
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            if (entitiesByQuad.get(q).contains(entityId)) {
                return q;
            }
        }
        return SpatialQuadrant.NW;
    }

    public int entityCount(SpatialQuadrant q) {
        return entitiesByQuad.get(q).size();
    }

    public ConcurrentLinkedQueue<String> entities(SpatialQuadrant q) {
        return entitiesByQuad.get(q);
    }
}
