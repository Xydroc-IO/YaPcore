package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockModelRegistryTest {

    @Test
    void classifiesCommonNonCubes() {
        assertEquals(BlockModelRegistry.Kind.STAIRS, BlockModelRegistry.classify("OAK_STAIRS"));
        assertEquals(BlockModelRegistry.Kind.SLAB, BlockModelRegistry.classify("STONE_SLAB"));
        assertEquals(BlockModelRegistry.Kind.CARPET, BlockModelRegistry.classify("WHITE_CARPET"));
        assertEquals(BlockModelRegistry.Kind.FENCE, BlockModelRegistry.classify("OAK_FENCE"));
        assertEquals(BlockModelRegistry.Kind.WALL, BlockModelRegistry.classify("COBBLESTONE_WALL"));
        assertEquals(BlockModelRegistry.Kind.CUBE, BlockModelRegistry.classify("STONE"));
        assertEquals(BlockModelRegistry.Kind.CUBE, BlockModelRegistry.classify("OAK_FENCE_GATE"));
    }

    @Test
    void bottomSlabIsHalfHeight() {
        BlockModelRegistry.ModelBox[] boxes = BlockModelRegistry.boxes(
                BlockModelRegistry.Kind.SLAB, BlockModelRegistry.SLAB_BOTTOM);
        assertEquals(1, boxes.length);
        assertEquals(0.5f, boxes[0].sy(), 1e-4f);
        assertEquals(0f, boxes[0].oy(), 1e-4f);
    }

    @Test
    void topSlabSitsOnUpperHalf() {
        BlockModelRegistry.ModelBox[] boxes = BlockModelRegistry.boxes(
                BlockModelRegistry.Kind.SLAB, BlockModelRegistry.SLAB_TOP);
        assertEquals(1, boxes.length);
        assertEquals(0.5f, boxes[0].oy(), 1e-4f);
        assertEquals(0.5f, boxes[0].sy(), 1e-4f);
    }

    @Test
    void stairsEmitTwoBoxesDistinctFromFullCube() {
        BlockModelRegistry.ModelBox[] boxes = BlockModelRegistry.boxes(
                BlockModelRegistry.Kind.STAIRS,
                BlockModelRegistry.packStairs(BlockModelRegistry.FACING_EAST, false));
        assertEquals(2, boxes.length);
        float volume = 0;
        for (BlockModelRegistry.ModelBox b : boxes) {
            volume += b.sx() * b.sy() * b.sz();
        }
        assertTrue(volume < 1.0f);
        assertTrue(volume > 0.5f);
    }

    @Test
    void fencePostIsNarrowerThanFullCube() {
        BlockModelRegistry.ModelBox[] boxes = BlockModelRegistry.boxes(
                BlockModelRegistry.Kind.FENCE, 0);
        assertEquals(1, boxes.length);
        assertTrue(boxes[0].sx() < 0.5f);
        assertTrue(boxes[0].sz() < 0.5f);
        assertEquals(1f, boxes[0].sy(), 1e-4f);
    }

    @Test
    void fenceWithConnectionsAddsBars() {
        int state = BlockModelRegistry.packFence(true, false, true, false);
        BlockModelRegistry.ModelBox[] boxes = BlockModelRegistry.boxes(
                BlockModelRegistry.Kind.FENCE, state);
        assertTrue(boxes.length > 1);
    }

    @Test
    void appendPackedWritesMilliblockStride() {
        int[] buf = new int[64];
        int written = BlockModelRegistry.appendPacked(
                buf, 0, 2, 64, 3, BlockModelRegistry.Kind.SLAB,
                BlockModelRegistry.SLAB_BOTTOM, 0xabcdef);
        assertEquals(GreedyMesher.STRIDE, written);
        assertEquals(MeshUnits.ofBlocks(2), buf[0]);
        assertEquals(MeshUnits.ofBlocks(64), buf[1]);
        assertEquals(MeshUnits.ofBlocks(3), buf[2]);
        assertEquals(MeshUnits.UNIT, buf[3]);
        assertEquals(MeshUnits.UNIT / 2, buf[4]);
        assertEquals(MeshUnits.UNIT, buf[5]);
        assertEquals(0xabcdef, buf[6]);
    }

    @Test
    void mesherEmitsModelBoxesInsteadOfFullCube() {
        int[][][] rgb = new int[1][1][1];
        rgb[0][0][0] = 0x778899;
        byte[][][] kinds = new byte[1][1][1];
        kinds[0][0][0] = (byte) BlockModelRegistry.Kind.SLAB.ordinal();
        byte[][][] states = new byte[1][1][1];
        states[0][0][0] = (byte) BlockModelRegistry.SLAB_BOTTOM;
        ChunkMeshData data = GreedyMesher.mesh(0, 0, rgb, kinds, states, 10);
        assertEquals(1, data.blockCount());
        assertEquals(MeshUnits.UNIT / 2, data.packed()[4]);
    }
}
