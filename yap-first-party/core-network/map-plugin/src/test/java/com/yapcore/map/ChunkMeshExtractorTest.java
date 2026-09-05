package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMeshExtractorTest {

    @Test
    void extractsSolidVoxelsFromDenseBuffer() {
        int[][][] rgb = new int[16][3][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 16; z++) {
                    rgb[x][y][z] = -1;
                }
            }
        }
        rgb[0][0][0] = 0x5f9f35;
        rgb[1][2][3] = 0x7d7d7d;

        ChunkMeshData data = ChunkMeshExtractor.extract(2, -1, rgb, 64);
        assertEquals(2, data.chunkX());
        assertEquals(-1, data.chunkZ());
        assertEquals(2, data.blockCount());
        int[] p = data.packed();
        assertEquals(0, p[0]);
        assertEquals(64, p[1]);
        assertEquals(0, p[2]);
        assertEquals(0x5f9f35, p[3]);
        assertEquals(1, p[4]);
        assertEquals(66, p[5]);
        assertEquals(3, p[6]);
        assertEquals(0x7d7d7d, p[7]);
    }

    @Test
    void growablePackedAppendsVoxels() {
        ChunkMeshExtractor.GrowablePacked g = new ChunkMeshExtractor.GrowablePacked(2);
        ChunkMeshExtractor.appendVoxel(g, 4, 70, 5, 0xaabbcc);
        ChunkMeshExtractor.appendVoxel(g, 0, -1, 15, 0x112233);
        ChunkMeshData data = g.toData(0, 0);
        assertEquals(2, data.blockCount());
        assertEquals(70, data.packed()[1]);
        assertEquals(-1, data.packed()[5]);
        assertEquals(0x112233, data.packed()[7]);
    }

    @Test
    void netherColumnMaxYCapsAtRoof() {
        int cap = ChunkMeshExtractor.columnMaxY(-1, 256, 0, 320);
        assertEquals(126, cap);
        int overworld = ChunkMeshExtractor.columnMaxY(0, 384, -64, 200);
        assertEquals(200, overworld);
        assertTrue(overworld < 383);
    }
}
