package com.yapcore.bridge;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Forwards legacy Spigot-compat mutations into the YapEngine Compatibility Bridge
 * so they execute on spatial tick boundaries with SequenceTokens.
 */
public final class ForwardingCompatibilityBridge extends CompatibilityBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.ForwardBridge");

    private final Supplier<com.yaplabs.yapengine.bridge.CompatibilityBridge> yapBridge;

    public ForwardingCompatibilityBridge(Supplier<com.yaplabs.yapengine.bridge.CompatibilityBridge> yapBridge) {
        this.yapBridge = yapBridge;
    }

    @Override
    public void start() {
        // YapEngine owns the real bridge thread
        LOG.info("Forwarding Compatibility Bridge armed (→ YapEngine Thread 9)");
    }

    @Override
    public void stop() {
    }

    @Override
    public void submitLegacyMutation(String source, String description, Runnable action) {
        com.yaplabs.yapengine.bridge.CompatibilityBridge target = yapBridge.get();
        if (target == null) {
            super.submitLegacyMutation(source, description, action);
            return;
        }
        target.submit(source, description, SpatialQuadrant.NW, action);
        LOG.fine(() -> "Forwarded " + description + " seq via YapEngine bridge");
    }

    @Override
    public int drainForTick() {
        return 0; // YapEngine spatial loops drain the real bridge
    }

    @Override
    public int pendingCount() {
        var b = yapBridge.get();
        return b == null ? super.pendingCount() : b.pendingCount();
    }

    @Override
    public long getSubmitted() {
        var b = yapBridge.get();
        return b == null ? super.getSubmitted() : b.getSubmitted();
    }

    @Override
    public long getDrained() {
        var b = yapBridge.get();
        return b == null ? super.getDrained() : b.getDrained();
    }

    @Override
    public Thread getBridgeThread() {
        var b = yapBridge.get();
        return b == null ? super.getBridgeThread() : b.getBridgeThread();
    }
}
