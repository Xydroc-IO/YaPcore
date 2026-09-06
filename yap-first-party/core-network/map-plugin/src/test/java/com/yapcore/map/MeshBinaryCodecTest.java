package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshBinaryCodecTest {

    @Test
    void encodeDecodeRoundTrip() {
        int[] packed = {
                0, 64000, 0, 1000, 500, 1000, 0x5f9f35,
                1000, 64000, 0, 2000, 1000, 1000, 0x7d7d7d
        };
        ChunkMeshData data = ChunkMeshData.boxes(3, -2, packed);
        byte[] bytes = MeshBinaryCodec.encode(data);
        assertTrue(MeshBinaryCodec.looksLikeYmesh(bytes));
        assertTrue(bytes.length < MeshEncoder.encode(data).length());

        ChunkMeshData decoded = MeshBinaryCodec.decode(bytes);
        assertEquals(3, decoded.chunkX());
        assertEquals(-2, decoded.chunkZ());
        assertEquals(2, decoded.blockCount());
        assertArrayEquals(packed, decoded.packed());
    }

    @Test
    void binarySmallerThanJsonForDenseChunk() {
        int[] packed = new int[70];
        for (int i = 0; i < 10; i++) {
            int o = i * 7;
            packed[o] = i * 1000;
            packed[o + 1] = 64000;
            packed[o + 2] = 0;
            packed[o + 3] = 1000;
            packed[o + 4] = 1000;
            packed[o + 5] = 1000;
            packed[o + 6] = 0x111111 * (i + 1);
        }
        ChunkMeshData data = ChunkMeshData.boxes(0, 0, packed);
        int jsonLen = MeshEncoder.encode(data).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int binLen = MeshBinaryCodec.encode(data).length;
        assertTrue(binLen < jsonLen, "binary=" + binLen + " json=" + jsonLen);
    }

    @Test
    void emptyMeshEncodes() {
        ChunkMeshData empty = ChunkMeshData.boxes(1, 1, new int[0]);
        ChunkMeshData decoded = MeshBinaryCodec.decode(MeshBinaryCodec.encode(empty));
        assertEquals(0, decoded.blockCount());
        assertEquals(1, decoded.chunkX());
    }
}
