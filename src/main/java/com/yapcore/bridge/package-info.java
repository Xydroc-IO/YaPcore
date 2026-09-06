/**
 * Product Compatibility Bridge facade over the chassis spatial bridge.
 *
 * <p>{@link com.yapcore.bridge.CompatibilityBridge} is the thin product API
 * (submit + metrics); production obtains it via
 * {@link com.yapcore.YaPcoreEngine#bridge()} as a
 * {@link com.yapcore.bridge.ForwardingCompatibilityBridge} that forwards into
 * {@link com.yaplabs.yapengine.bridge.CompatibilityBridge}. Do not construct a
 * bare product bridge for live lifecycle.
 */
package com.yapcore.bridge;
