/**
 * YaPcore <b>product</b> surface: gateway, crossplay, dashboard, Paper glue,
 * protocol, and first-party plugin APIs.
 *
 * <p>Stable entry is {@link com.yapcore.YaPcoreEngine} over the chassis
 * {@link com.yaplabs.yapengine.YapEngine}. Product
 * {@link com.yapcore.network.TrafficCop} is the game-event Netty ingest used by
 * {@code YaPcoreEngine} / dual-stack paths — distinct from the chassis
 * {@code com.yaplabs.yapengine.network.traffic.TrafficCop}. Plugins must not
 * import {@code com.yaplabs.yapengine}; use {@code YapSched} and
 * {@code ServicesManager}.
 *
 * @see com.yaplabs.yapengine
 */
package com.yapcore;
