package com.yapcore.pregen.shape;

/** Chunk column coordinate. */
public record ChunkPos(int x, int z) {
    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }
}
