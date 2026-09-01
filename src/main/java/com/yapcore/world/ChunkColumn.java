package com.yapcore.world;

/**
 * One 16×384×16 column (−64…320), 24 sections of 4096 block states each.
 */
public final class ChunkColumn {

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int SECTION_COUNT = HEIGHT / 16; // 24
    public static final int SECTION_VOLUME = 16 * 16 * 16;

    private final int chunkX;
    private final int chunkZ;
    /** sections[sectionIndex][index] — index = ((y & 15) << 8) | (z << 4) | x */
    private final int[][] sections = new int[SECTION_COUNT][SECTION_VOLUME];

    public ChunkColumn(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int[] section(int sectionIndex) {
        return sections[sectionIndex];
    }

    public int getBlock(int worldX, int worldY, int worldZ) {
        if (worldY < MIN_Y || worldY >= MIN_Y + HEIGHT) {
            return BlockStates.AIR;
        }
        int lx = worldX & 15;
        int lz = worldZ & 15;
        int sectionIndex = (worldY - MIN_Y) >> 4;
        int ly = worldY & 15;
        return sections[sectionIndex][pack(lx, ly, lz)];
    }

    public void setBlock(int worldX, int worldY, int worldZ, int state) {
        if (worldY < MIN_Y || worldY >= MIN_Y + HEIGHT) {
            return;
        }
        int lx = worldX & 15;
        int lz = worldZ & 15;
        int sectionIndex = (worldY - MIN_Y) >> 4;
        int ly = worldY & 15;
        sections[sectionIndex][pack(lx, ly, lz)] = state;
    }

    /**
     * Heightmap value (relative to {@link #MIN_Y}): first air above highest non-air, or 0.
     */
    public int surfaceHeightLocal(int localX, int localZ) {
        for (int y = MIN_Y + HEIGHT - 1; y >= MIN_Y; y--) {
            int sectionIndex = (y - MIN_Y) >> 4;
            int ly = y & 15;
            if (sections[sectionIndex][pack(localX, ly, localZ)] != BlockStates.AIR) {
                return (y + 1) - MIN_Y;
            }
        }
        return 0;
    }

    private static int pack(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}
