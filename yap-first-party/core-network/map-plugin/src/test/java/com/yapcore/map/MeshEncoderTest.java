package com.yapcore.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshEncoderTest {

    @Test
    void encodeDecodeRoundTrip() {
        int[] packed = {1, 64, 2, 1, 1, 1, 0x5f9f35, 3, 65, 4, 2, 1, 1, 0x7d7d7d};
        ChunkMeshData data = ChunkMeshData.boxes(5, -3, packed);
        String json = MeshEncoder.encode(data);
        assertTrue(json.contains("\"v\":2"));
        assertTrue(json.contains("\"cx\":5"));
        assertTrue(json.contains("\"cz\":-3"));
        assertTrue(json.contains("\"n\":2"));
        assertEquals(2, MeshEncoder.parseBlockCount(json));
        assertEquals(2, MeshEncoder.parseFormatVersion(json));

        ChunkMeshData decoded = MeshEncoder.decode(json);
        assertEquals(5, decoded.chunkX());
        assertEquals(-3, decoded.chunkZ());
        assertEquals(ChunkMeshData.FORMAT_V2, decoded.formatVersion());
        assertEquals(2, decoded.blockCount());
        assertEquals(0x5f9f35, decoded.packed()[6]);
        assertEquals(0x7d7d7d, decoded.packed()[13]);
    }

    @Test
    void decodeStillReadsV1UnitCubes() {
        String v1 = "{\"v\":1,\"cx\":1,\"cz\":2,\"n\":1,\"d\":[0,64,0,255]}";
        ChunkMeshData decoded = MeshEncoder.decode(v1);
        assertEquals(1, decoded.formatVersion());
        assertEquals(1, decoded.blockCount());
        assertEquals(4, decoded.stride());
        assertEquals(64, decoded.packed()[1]);
    }

    @Test
    void writeChunkAndManifest(@TempDir Path tempDir) throws Exception {
        ChunkMeshData a = ChunkMeshData.boxes(0, 0, new int[] {0, 64, 0, 1, 1, 1, 0x111111});
        ChunkMeshData b = ChunkMeshData.boxes(1, 0, new int[] {
                1, 65, 1, 1, 1, 1, 0x222222, 2, 66, 2, 1, 1, 1, 0x333333});
        Path pathA = MeshEncoder.writeChunk(tempDir, "world", MapLayerSampler.LAYER_FULL, a);
        Path pathB = MeshEncoder.writeChunk(tempDir, "world", MapLayerSampler.LAYER_FULL, b);
        assertTrue(Files.isRegularFile(pathA));
        assertTrue(pathA.toString().endsWith("world/full/0_0.json"));
        assertTrue(Files.isRegularFile(pathB));

        Path manifest = MeshEncoder.writeManifest(tempDir, "world", MapLayerSampler.LAYER_FULL, 10, 20, 8);
        assertTrue(Files.isRegularFile(manifest));
        String body = Files.readString(manifest);
        assertTrue(body.contains("\"world\":\"world\""));
        assertTrue(body.contains("\"layer\":\"full\""));
        assertTrue(body.contains("\"originChunkX\":10"));
        assertTrue(body.contains("\"originChunkZ\":20"));
        assertTrue(body.contains("\"radius\":8"));
        assertTrue(body.contains("\"file\":\"0_0.json\""));
        assertTrue(body.contains("\"blocks\":1"));
        assertTrue(body.contains("\"blocks\":2"));
    }
}
