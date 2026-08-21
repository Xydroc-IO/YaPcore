package com.yapcore.paper.phase3;

/**
 * Process-global holder so Paper (same JVM, child classloader) can reach the
 * Phase 3 bridge via the application / system classloader.
 */
public final class PaperTickBridgeHolder {

    public static volatile PaperTickBridge BRIDGE;
    public static volatile YapSpatialTickCoordinator COORDINATOR;
    /** Threads 7–8 facade — leases + border handoffs. */
    public static volatile Object SYNC_LAYER; // ChunkSyncLayer (typed as Object for plugin reflection)
    /** When true, plugin performs interior NMS entity tick under leases. */
    public static volatile boolean NMS_TICK_ENABLED = true;

    private PaperTickBridgeHolder() {
    }
}
