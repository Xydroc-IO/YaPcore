package com.yapcore.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapExtentTest {

    @Test
    void readsGeneratedChunksIncludingNegativeRegions(@TempDir Path dir) throws Exception {
        writeChunk(dir.resolve("r.0.0.mca"), 0);
        writeChunk(dir.resolve("r.-1.0.mca"), 0);
        MapExtent.Snapshot snap = MapExtent.scanRegions(dir);
        assertTrue(snap.present());
        assertEquals(-32, snap.minChunkX());
        assertEquals(0, snap.minChunkZ());
        assertEquals(2, snap.chunks().size());
        assertEquals(0, snap.chunks().get(0)[0]);
        assertEquals(-32, snap.chunks().get(0)[2]);
        assertEquals(32, snap.chunks().get(1)[0]);
        assertEquals(0, snap.chunks().get(1)[2]);
    }

    @Test
    void emptyDirectoryIsAbsent(@TempDir Path dir) {
        assertTrue(MapExtent.scanRegions(dir).chunks().isEmpty());
    }

    /** One generated chunk at local index {@code slot} (location offset 2, size 1). */
    private static void writeChunk(Path file, int slot) throws Exception {
        byte[] header = new byte[4096];
        int o = slot * 4;
        header[o] = 0;
        header[o + 1] = 0;
        header[o + 2] = 2;
        header[o + 3] = 1;
        Files.write(file, header);
    }
}
