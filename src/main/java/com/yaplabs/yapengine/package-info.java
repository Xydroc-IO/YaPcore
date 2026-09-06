/**
 * YapLabs concurrency / spatial <b>chassis</b> ({@code yapengine} brand).
 *
 * <p>Owns {@link com.yaplabs.yapengine.YapEngine}, spatial loops, sequencing,
 * sync/lease/boundary, sandbox pools, and chassis-adjacent Netty helpers
 * (including {@link com.yaplabs.yapengine.network.traffic.TrafficCop} —
 * sequencer / Epoll ingest path). Kept as a permanent package root; product
 * code lives under {@code com.yapcore} and imports chassis types deliberately.
 *
 * @see com.yapcore
 */
package com.yaplabs.yapengine;
