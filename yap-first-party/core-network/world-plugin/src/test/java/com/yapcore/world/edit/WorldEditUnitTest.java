package com.yapcore.world.edit;

import com.yapcore.world.CuboidSelection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditUnitTest {

    @Test
    void expressionArithmeticAndCompare() {
        assertEquals(5.0, ExpressionEngine.eval("2+3", 0, 0, 0, 0, 0, 0, 0), 1e-9);
        assertEquals(1.0, ExpressionEngine.eval("x", 1, 0, 0, 0, 0, 0, 0), 1e-9);
        assertTrue(ExpressionEngine.test("y > 0.5", 0, 0.9, 0, 0, 0, 0, 0));
        assertFalse(ExpressionEngine.test("y > 0.5", 0, 0.1, 0, 0, 0, 0, 0));
        assertEquals(0.0, ExpressionEngine.eval("", 0, 0, 0, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void selectionModeParseAndPolyClear() {
        SelectionShape shapes = new SelectionShape();
        UUID id = UUID.randomUUID();
        assertEquals(SelectionShape.Mode.CUBOID, shapes.mode(id));
        assertTrue(shapes.setMode(id, "sphere"));
        assertEquals(SelectionShape.Mode.SPHERE, shapes.mode(id));
        assertTrue(shapes.setMode(id, "poly"));
        shapes.addPolyPoint(id, 0, 64, 0);
        shapes.addPolyPoint(id, 2, 64, 0);
        assertEquals(2, shapes.polyPoints(id).size());
        shapes.clear(id);
        assertEquals(SelectionShape.Mode.CUBOID, shapes.mode(id));
        assertTrue(shapes.polyPoints(id).isEmpty());
        assertFalse(shapes.setMode(id, "nope"));
    }

    @Test
    void sphereContainsCenterOfCuboid() {
        SelectionShape shapes = new SelectionShape();
        UUID id = UUID.randomUUID();
        shapes.setMode(id, SelectionShape.Mode.SPHERE);
        CuboidSelection sel = new CuboidSelection("world", 0, 0, 0, 4, 4, 4);
        assertTrue(shapes.contains(id, sel, 2, 2, 2));
        assertFalse(shapes.contains(id, sel, 0, 0, 0));
    }
}
