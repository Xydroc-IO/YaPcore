package com.yapcore.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TileRendererTest {

    @Test
    void writesSampleTile(@TempDir Path tempDir) throws Exception {
        MapConfig config = new MapConfig(null);
        TileRenderer renderer = new TileRenderer(config, tempDir);
        Path tile = renderer.writeSampleTile("world", 0, 0);
        assertTrue(Files.isRegularFile(tile));
        assertTrue(tile.toString().endsWith("world/0/0_0.png"));

        Path smokeRoot = Path.of("build/smoke-yap-map/tiles");
        Files.createDirectories(smokeRoot);
        TileRenderer smokeRenderer = new TileRenderer(config, smokeRoot);
        Path smokeTile = smokeRenderer.writeSampleTile("world", 0, 0);
        assertTrue(Files.isRegularFile(smokeTile));
    }
}
