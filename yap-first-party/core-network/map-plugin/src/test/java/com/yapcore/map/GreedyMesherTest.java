package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyMesherTest {

    @Test
    void hiddenFaceCullReducesFaceCount() {
        // Solid 2×2×2 cube inside a larger empty grid.
        boolean[][][] solid = new boolean[4][4][4];
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 2; z++) {
                    solid[x][y][z] = true;
                }
            }
        }
        int naive = GreedyMesher.naiveFaceCount(solid);
        int exposed = GreedyMesher.countExposedFaces(solid);
        assertEquals(8 * 6, naive);
        assertEquals(24, exposed); // outer surface of a 2×2×2 cube
        assertTrue(exposed < naive);
    }

    @Test
    void fullyInternalVoxelHasZeroContribution() {
        boolean[][][] solid = new boolean[3][3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    solid[x][y][z] = true;
                }
            }
        }
        int exposed = GreedyMesher.countExposedFaces(solid);
        // 3×3×3 cube: 6 faces × 9 cells = 54
        assertEquals(54, exposed);
        assertTrue(exposed < GreedyMesher.naiveFaceCount(solid));
    }

    @Test
    void faceCulledUnitBoxesDropsBuriedVoxels() {
        int[][][] rgb = fillCube(0, 0, 0, 3, 3, 3, 0x888888);
        // bury center by ensuring 3×3×3 — only shell remains after cull
        ChunkMeshData culled = GreedyMesher.faceCulledUnitBoxes(0, 0, rgb, 0);
        int solid = 27;
        assertTrue(culled.blockCount() < solid);
        // center cell (1,1,1) is fully enclosed → not emitted
        assertEquals(26, culled.blockCount());
    }

    @Test
    void greedyMergesUniformCubeIntoOneBox() {
        int[][][] rgb = fillCube(0, 0, 0, 4, 4, 4, 0x5f9f35);
        ChunkMeshData data = GreedyMesher.mesh(1, 2, rgb, 64);
        assertEquals(ChunkMeshData.FORMAT_V2, data.formatVersion());
        assertEquals(1, data.blockCount());
        int[] p = data.packed();
        assertEquals(0, p[0]);
        assertEquals(MeshUnits.ofBlocks(64), p[1]);
        assertEquals(0, p[2]);
        assertEquals(MeshUnits.ofBlocks(4), p[3]);
        assertEquals(MeshUnits.ofBlocks(4), p[4]);
        assertEquals(MeshUnits.ofBlocks(4), p[5]);
        assertEquals(0x5f9f35, p[6]);
        assertEquals(1, data.chunkX());
        assertEquals(2, data.chunkZ());
    }

    @Test
    void differentColorsDoNotMerge() {
        int[][][] rgb = new int[2][1][1];
        rgb[0][0][0] = 0x111111;
        rgb[1][0][0] = 0x222222;
        ChunkMeshData data = GreedyMesher.mesh(0, 0, rgb, 0);
        assertEquals(2, data.blockCount());
    }

    private static int[][][] fillCube(int x0, int y0, int z0, int sx, int sy, int sz, int rgb) {
        int[][][] out = new int[x0 + sx][y0 + sy][z0 + sz];
        for (int x = 0; x < out.length; x++) {
            for (int y = 0; y < out[x].length; y++) {
                for (int z = 0; z < out[x][y].length; z++) {
                    out[x][y][z] = -1;
                }
            }
        }
        for (int x = x0; x < x0 + sx; x++) {
            for (int y = y0; y < y0 + sy; y++) {
                for (int z = z0; z < z0 + sz; z++) {
                    out[x][y][z] = rgb;
                }
            }
        }
        return out;
    }
}
