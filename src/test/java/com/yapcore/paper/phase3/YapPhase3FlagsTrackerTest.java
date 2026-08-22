package com.yapcore.paper.phase3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapPhase3FlagsTrackerTest {

    @AfterEach
    void clear() {
        System.clearProperty("yapcore.phase3.spatial-tracker");
        System.clearProperty("yapcore.phase3.spatial-tracker-skip-clean");
        System.clearProperty("yapcore.phase3.spatial-tracker-players");
        YapPhase3Flags.refresh();
    }

    @Test
    void skipCleanDefaultsOnWhenUnset() {
        System.clearProperty("yapcore.phase3.spatial-tracker-skip-clean");
        YapPhase3Flags.refresh();
        assertTrue(YapPhase3Flags.spatialTrackerSkipClean());
    }

    @Test
    void skipCleanCanDisable() {
        System.setProperty("yapcore.phase3.spatial-tracker-skip-clean", "false");
        YapPhase3Flags.refresh();
        assertFalse(YapPhase3Flags.spatialTrackerSkipClean());
    }

    @Test
    void spatialTrackerEnabledReadsFlag() {
        System.setProperty("yapcore.phase3.spatial-tracker", "true");
        YapPhase3Flags.refresh();
        assertTrue(YapPhase3Flags.spatialTracker());
        assertTrue(com.yapcore.paper.phase3.nms.InteriorWorldTickBridge.spatialTrackerEnabled());
    }

    @Test
    void trackerPlayersDefaultsOnWhenUnset() {
        System.clearProperty("yapcore.phase3.spatial-tracker-players");
        System.setProperty("yapcore.phase3.spatial-tracker", "true");
        YapPhase3Flags.refresh();
        assertTrue(YapPhase3Flags.spatialTrackerPlayers());
        assertTrue(com.yapcore.paper.phase3.nms.InteriorWorldTickBridge.spatialTrackerPlayersEnabled());
    }

    @Test
    void trackerPlayersCanDisable() {
        System.setProperty("yapcore.phase3.spatial-tracker", "true");
        System.setProperty("yapcore.phase3.spatial-tracker-players", "false");
        YapPhase3Flags.refresh();
        assertFalse(YapPhase3Flags.spatialTrackerPlayers());
        assertFalse(com.yapcore.paper.phase3.nms.InteriorWorldTickBridge.spatialTrackerPlayersEnabled());
    }

    @Test
    void trackerPlayersRequiresSpatialTracker() {
        System.setProperty("yapcore.phase3.spatial-tracker", "false");
        System.setProperty("yapcore.phase3.spatial-tracker-players", "true");
        YapPhase3Flags.refresh();
        assertTrue(YapPhase3Flags.spatialTrackerPlayers());
        assertFalse(com.yapcore.paper.phase3.nms.InteriorWorldTickBridge.spatialTrackerPlayersEnabled());
    }
}
