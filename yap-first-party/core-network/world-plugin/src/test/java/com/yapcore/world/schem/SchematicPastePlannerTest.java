package com.yapcore.world.schem;

import com.yapcore.world.edit.BlockBatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicPastePlannerTest {

    @Test
    void planOffsetsAndSkipsAir() {
        List<Schematic.BlockEntry> blocks = List.of(
                new Schematic.BlockEntry(0, 0, 0, "STONE"),
                new Schematic.BlockEntry(1, 0, 0, "AIR"),
                new Schematic.BlockEntry(0, 1, 0, "minecraft:air"),
                new Schematic.BlockEntry(2, 3, 4, "OAK_LOG[axis=y]", "chest={}")
        );
        Schematic schem = new Schematic("world", 0, 0, 0, blocks);
        List<BlockBatch.Encoded> plans = SchematicPastePlanner.plan(
                schem, 100, 64, 200,
                new SchematicPastePlanner.Options(true, false, null, null));
        assertEquals(2, plans.size());
        assertEquals(100, plans.get(0).x());
        assertEquals(64, plans.get(0).y());
        assertEquals(200, plans.get(0).z());
        assertEquals("STONE", plans.get(0).encoded());
        assertEquals(102, plans.get(1).x());
        assertEquals(67, plans.get(1).y());
        assertEquals(204, plans.get(1).z());
        assertEquals("chest={}", plans.get(1).tileNbt());
    }

    @Test
    void sectionSortOrdersByChunkThenSectionY() {
        List<BlockBatch.Encoded> plans = new ArrayList<>(List.of(
                new BlockBatch.Encoded(20, 5, 4, "A"),
                new BlockBatch.Encoded(4, 20, 4, "B"),
                new BlockBatch.Encoded(4, 5, 20, "C"),
                new BlockBatch.Encoded(4, 5, 4, "D")
        ));
        SchematicPastePlanner.sortBySection(plans);
        // chunk Z then X then section Y: (0,0,s0)=D, (0,0,s1)=B, (1,0)=A, (0,1)=C
        assertEquals("D", plans.get(0).encoded());
        assertEquals("B", plans.get(1).encoded());
        assertEquals("A", plans.get(2).encoded());
        assertEquals("C", plans.get(3).encoded());
    }

    @Test
    void sectionKeyStableForSameSection() {
        long a = SchematicPastePlanner.sectionKey(16, 32, 48);
        long b = SchematicPastePlanner.sectionKey(17, 33, 49);
        long c = SchematicPastePlanner.sectionKey(32, 32, 48);
        assertEquals(a, b);
        assertTrue(a != c);
    }

    @Test
    void isAirEncodedRecognizesVariants() {
        assertTrue(SchematicPastePlanner.isAirEncoded("AIR"));
        assertTrue(SchematicPastePlanner.isAirEncoded("minecraft:air"));
        assertTrue(SchematicPastePlanner.isAirEncoded("CAVE_AIR"));
        assertTrue(SchematicPastePlanner.isAirEncoded(null));
    }
}
