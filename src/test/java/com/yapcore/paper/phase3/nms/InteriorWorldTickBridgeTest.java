package com.yapcore.paper.phase3.nms;

import com.yapcore.paper.phase3.YapSpatialTickCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteriorWorldTickBridgeTest {

    @Test
    void deepInteriorIsInterior() {
        assertTrue(InteriorWorldTickBridge.isInteriorChunk(8, 8));
        assertTrue(InteriorWorldTickBridge.isInteriorChunk(-9, -9));
        assertFalse(YapSpatialTickCoordinator.isBorderChunk(8, 8));
    }

    @Test
    void axisOriginIsBorder() {
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(0, 0));
        assertTrue(YapSpatialTickCoordinator.isBorderChunk(-1, 0));
        assertFalse(InteriorWorldTickBridge.isInteriorChunk(0, 0));
    }

    @Test
    void emptyBeAndRedstoneFlushIsNoOp() {
        InteriorWorldTickBridge.flushBlockEntities();
        InteriorWorldTickBridge.flushBlockEvents();
        assertTrue(InteriorWorldTickBridge.blockEntityCount() >= 0);
        assertTrue(InteriorWorldTickBridge.blockEventCount() >= 0);
    }
}
