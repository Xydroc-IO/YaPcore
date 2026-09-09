package com.yapcore.world.schem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicTransformsTest {

    @Test
    void rotateYSwapsFootprintAndFacing() {
        Schematic src = new Schematic("world", 0, 0, 0, List.of(
                new Schematic.BlockEntry(0, 0, 0, "STONE"),
                new Schematic.BlockEntry(2, 0, 0, "OAK_STAIRS|minecraft:oak_stairs[facing=north,half=bottom]")
        ));
        Schematic out = SchematicTransforms.rotateY(src, 90);
        Schematic.Bounds b = out.bounds();
        // 3×1×1 → after CW and normalize → 1×1×3
        assertEquals(1, b.sizeX());
        assertEquals(1, b.sizeY());
        assertEquals(3, b.sizeZ());
        boolean foundEast = false;
        for (Schematic.BlockEntry e : out.blocks()) {
            if (e.encoded() != null && e.encoded().contains("facing=east")) {
                foundEast = true;
            }
        }
        assertTrue(foundEast, "north stairs should face east after +90°");
    }

    @Test
    void rotateEncodedCyclesFacing() {
        String enc = "OAK_STAIRS|minecraft:oak_stairs[facing=north,half=bottom]";
        assertEquals("OAK_STAIRS|minecraft:oak_stairs[facing=east,half=bottom]",
                SchematicTransforms.rotateEncodedY(enc, 1));
        assertEquals("OAK_STAIRS|minecraft:oak_stairs[facing=south,half=bottom]",
                SchematicTransforms.rotateEncodedY(enc, 2));
    }
}
