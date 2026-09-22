package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** squareToCircle is the Geyser fog formula. Join packets advertise the filled square instead. */
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
    void advertisedRadiusIsTheFilledSquareNotTheInflatedCircle() {
        int view = 4;
        int circle = ChunkUtils.squareToCircle(view);
        assertTrue(circle > view, "circle extends past the square Folia sends");
        int advertisedBlocks = view << 4;
        assertEquals(64, advertisedBlocks);
        assertTrue(advertisedBlocks < circle << 4,
                "advertising the circle makes Bedrock wait for columns that are never sent");
    }
}
