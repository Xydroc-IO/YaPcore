package com.yapcore.portals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalShapeTest {

    @Test
    void frameIsPerimeterOfAFlatPad() {
        PortalCuboid wall = PortalCuboid.of(0, 0, 0, 4, 4, 0);
        assertTrue(PortalShape.frame(0, 0, 0, 4, 4, 0));
        assertTrue(PortalShape.frame(2, 0, 0, 4, 4, 0));
        assertFalse(PortalShape.frame(2, 2, 0, 4, 4, 0));
    }

    @Test
    void ovalKeepsCenterAndDropsCorner() {
        assertTrue(PortalShape.oval(2, 2, 0, 4, 4, 0));
        assertFalse(PortalShape.oval(0, 0, 0, 4, 4, 0));
    }

    @Test
    void crossIsTheCenterLines() {
        assertTrue(PortalShape.cross(2, 0, 0, 4, 4, 0));
        assertTrue(PortalShape.cross(0, 2, 0, 4, 4, 0));
        assertFalse(PortalShape.cross(0, 0, 0, 4, 4, 0));
    }

    @Test
    void customMaskIsRelativeToMin() {
        PortalCuboid box = PortalCuboid.of(10, 64, 10, 12, 66, 10);
        PortalShape shape = PortalShape.of(PortalShape.Kind.CUSTOM).withBlock(1, 0, 0, true);
        assertTrue(shape.contains(box, 11, 64, 10));
        assertFalse(shape.contains(box, 10, 64, 10));
    }

    @Test
    void thickDoorwayStaysAFlatShape() {
        assertFalse(PortalShape.oval(0, 0, 0, 6, 8, 1));
        assertFalse(PortalShape.oval(0, 0, 1, 6, 8, 1));
        assertTrue(PortalShape.oval(3, 4, 0, 6, 8, 1));
        assertTrue(PortalShape.oval(3, 4, 1, 6, 8, 1));
        assertFalse(PortalShape.frame(3, 4, 0, 6, 8, 1));
        assertTrue(PortalShape.frame(0, 4, 1, 6, 8, 1));
    }

    @Test
    void ovalIsRounderThanTheBox() {
        int inside = 0;
        int box = 0;
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 8; y++) {
                box++;
                if (PortalShape.oval(x, y, 0, 6, 8, 0)) {
                    inside++;
                }
            }
        }
        assertTrue(inside < box * 8 / 10, "oval collapsed to a rectangle: " + inside + "/" + box);
        assertTrue(inside > box / 2, "oval collapsed to a speck: " + inside + "/" + box);
    }

    @Test
    void archIsADoorNotANibbledRectangle() {
        assertTrue(PortalShape.arch(0, 0, 0, 6, 8, 0));
        assertTrue(PortalShape.arch(3, 8, 0, 6, 8, 0));
        assertFalse(PortalShape.arch(0, 8, 0, 6, 8, 0));
        assertFalse(PortalShape.arch(0, 7, 0, 6, 8, 0));
        assertTrue(PortalShape.arch(0, 0, 0, 8, 5, 1));
        assertFalse(PortalShape.arch(0, 5, 0, 8, 5, 1));
        assertTrue(PortalShape.arch(4, 5, 1, 8, 5, 1));
    }

    @Test
    void ringIsHollowAndCrossStaysCentered() {
        assertFalse(PortalShape.ring(4, 4, 0, 8, 8, 0));
        assertTrue(PortalShape.ring(0, 4, 0, 8, 8, 0));
        assertFalse(PortalShape.ring(0, 0, 0, 8, 8, 0));
        assertTrue(PortalShape.cross(2, 0, 0, 5, 5, 0));
        assertTrue(PortalShape.cross(3, 0, 0, 5, 5, 0));
        assertFalse(PortalShape.cross(0, 0, 0, 5, 5, 0));
    }

    @Test
    void fullCoversTheCuboid() {
        PortalCuboid box = PortalCuboid.of(0, 0, 0, 1, 1, 0);
        assertTrue(PortalShape.full().contains(box, 1, 1, 0));
        assertFalse(PortalShape.full().contains(box, 3, 0, 0));
    }
}
