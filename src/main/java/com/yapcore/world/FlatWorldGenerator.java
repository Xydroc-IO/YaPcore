package com.yapcore.world;

/**
 * Classic flat: bedrock floor, stone, dirt, grass at Y=64.
 */
public final class FlatWorldGenerator {

    private FlatWorldGenerator() {
    }

    public static ChunkColumn generate(int chunkX, int chunkZ) {
        ChunkColumn col = new ChunkColumn(chunkX, chunkZ);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                col.setBlock(wx, ChunkColumn.MIN_Y, wz, BlockStates.BEDROCK);
                for (int y = ChunkColumn.MIN_Y + 1; y < 63; y++) {
                    col.setBlock(wx, y, wz, BlockStates.STONE);
                }
                col.setBlock(wx, 63, wz, BlockStates.DIRT);
                col.setBlock(wx, 64, wz, BlockStates.GRASS_BLOCK);
            }
        }
        return col;
    }
}
