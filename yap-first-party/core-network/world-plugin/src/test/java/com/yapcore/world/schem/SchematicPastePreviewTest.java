package com.yapcore.world.schem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicPastePreviewTest {

    @Test
    void pendingBoundsFromSchematic() {
        Schematic schem = new Schematic("w", 0, 0, 0, List.of(
                new Schematic.BlockEntry(0, 0, 0, "STONE"),
                new Schematic.BlockEntry(4, 2, 3, "DIRT")));
        SchematicPastePreview.Pending pending = new SchematicPastePreview.Pending(
                "test.yschem", schem, "world", 10, 64, 20, false);
        assertEquals(10, pending.originX());
        assertEquals(14, pending.maxX());
        assertEquals(66, pending.maxY());
        assertEquals(23, pending.maxZ());
        assertEquals(5, pending.bounds().sizeX());
        assertEquals(3, pending.bounds().sizeY());
        assertEquals(4, pending.bounds().sizeZ());
    }

    @Test
    void emptySchematicBoundsAreZero() {
        Schematic schem = new Schematic("w", 0, 0, 0, List.of());
        assertEquals(0, schem.bounds().sizeX());
        assertEquals(0, schem.bounds().sizeY());
        assertEquals(0, schem.bounds().sizeZ());
    }
}
