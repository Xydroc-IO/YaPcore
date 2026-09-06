package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LodMesherTest {

    @Test
    void lod1DropsThinCarpetLikeBoxes() {
        int[] packed = {
                0, 0, 0, MeshUnits.UNIT, MeshUnits.of(1f / 16f), MeshUnits.UNIT, 0xaa0000,
                0, MeshUnits.UNIT, 0, MeshUnits.UNIT, MeshUnits.UNIT, MeshUnits.UNIT, 0x00aa00
        };
        ChunkMeshData lod0 = ChunkMeshData.boxes(0, 0, packed);
        ChunkMeshData lod1 = LodMesher.buildLod1(lod0);
        assertEquals(1, lod1.blockCount());
        assertEquals(0x00aa00, lod1.packed()[6]);
    }

    @Test
    void lod1MergesAdjacentSameColorBoxes() {
        int u = MeshUnits.UNIT;
        int[] packed = {
                0, 0, 0, u, u, u, 0x111111,
                u, 0, 0, u, u, u, 0x111111
        };
        ChunkMeshData lod0 = ChunkMeshData.boxes(0, 0, packed);
        ChunkMeshData lod1 = LodMesher.buildLod1(lod0);
        assertEquals(1, lod1.blockCount());
        assertEquals(2 * u, lod1.packed()[3]);
    }

    @Test
    void lod2NotLargerThanLod1() {
        int u = MeshUnits.UNIT;
        int[] packed = new int[7 * 8];
        int n = 0;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    packed[n++] = x * u;
                    packed[n++] = y * u;
                    packed[n++] = z * u;
                    packed[n++] = u;
                    packed[n++] = u;
                    packed[n++] = u;
                    packed[n++] = 0xabcdef;
                }
            }
        }
        ChunkMeshData lod0 = ChunkMeshData.boxes(1, 1, packed);
        ChunkMeshData lod1 = LodMesher.buildLod1(lod0);
        ChunkMeshData lod2 = LodMesher.buildLod2(lod0);
        assertTrue(lod1.blockCount() <= lod0.blockCount());
        assertTrue(lod2.blockCount() <= lod1.blockCount());
        assertEquals(1, lod0.chunkX());
    }

    @Test
    void tryMergeXAdjacent() {
        int u = MeshUnits.UNIT;
        int[] a = {0, 0, 0, u, u, u, 1};
        int[] b = {u, 0, 0, u, u, u, 1};
        int[] m = LodMesher.tryMerge(a, b);
        assertEquals(2 * u, m[3]);
        assertEquals(0, m[0]);
    }
}
