package com.yapcore.network;

import com.yapcore.YaPcoreEngine;
import com.yapcore.model.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the intentional dual TrafficCop design: product GameEvent ingest and chassis
 * SequenceToken path both start under {@link YaPcoreEngine}; neither replaces the other.
 *
 * <p>Do not "merge to one" without an explicit architecture redesign — see
 * docs/ops/CODE_ELEGANCE_FOLLOWUP.md Track 2 Phase D.
 */
class DualTrafficCopContractTest {

    @Test
    void bothCopsStartAsDistinctTypesAndThreads() throws Exception {
        YaPcoreEngine engine = new YaPcoreEngine(29111);
        try {
            engine.start();
            assertTrue(engine.yapEngine().isRunning());

            TrafficCop product = engine.trafficCop();
            com.yaplabs.yapengine.network.traffic.TrafficCop chassis = engine.yapEngine().trafficCop();

            assertEquals("com.yapcore.network.TrafficCop", product.getClass().getName());
            assertEquals(
                    "com.yaplabs.yapengine.network.traffic.TrafficCop",
                    chassis.getClass().getName());

            Thread productThread = product.getCopThread();
            Thread chassisThread = chassis.getThread();
            assertNotNull(productThread);
            assertNotNull(chassisThread);
            assertTrue(productThread.isAlive(), "product TrafficCop thread");
            assertTrue(chassisThread.isAlive(), "chassis TrafficCop thread");
            assertNotEquals(productThread, chassisThread);
            assertEquals("yap-core2-traffic-cop", productThread.getName());
            assertEquals("yap-t2-traffic-cop", chassisThread.getName());
        } finally {
            engine.stop();
        }
    }

    @Test
    void productIngestRelaysToChassisSequencer() throws Exception {
        YaPcoreEngine engine = new YaPcoreEngine(29112);
        try {
            engine.start();
            var chassis = engine.yapEngine().trafficCop();
            long before = chassis.getIngested();

            engine.trafficCop().ingest(new GameEvent(
                    GameEvent.Type.PLAYER_CHAT,
                    "ContractBot",
                    Map.of("message", "dual-cop-contract")));

            long deadline = System.nanoTime() + 2_000_000_000L;
            while (chassis.getIngested() <= before && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(
                    chassis.getIngested() > before,
                    "relayLoop must forward GameEvent into chassis TrafficCop.ingest");
        } finally {
            engine.stop();
        }
    }
}
