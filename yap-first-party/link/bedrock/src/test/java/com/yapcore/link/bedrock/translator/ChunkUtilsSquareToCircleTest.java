package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Publisher / ChunkRadiusUpdated use blocks = squareToCircle(view) << 4. */
final class ChunkUtilsSquareToCircleTest {

    @Test
    void squareToCircleMatchesGeyserFormula() {
        // ceil((8+1)*√2) = 13
        assertEquals(13, ChunkUtils.squareToCircle(8));
        // ceil((32+1)*√2) = 47
        assertEquals(47, ChunkUtils.squareToCircle(32));
        // ceil((10+1)*√2) = 16
        assertEquals(16, ChunkUtils.squareToCircle(10));
    }

    @Test
    void publisherRadiusInBlocksIsCircleChunksTimes16() {
        int view = 32;
        int circle = ChunkUtils.squareToCircle(view);
        int blocks = circle << 4;
        assertEquals(47 * 16, blocks);
        assertTrue(blocks > 8 * 16, "publisher must exceed the old 8-chunk fog wall");
    }
}
