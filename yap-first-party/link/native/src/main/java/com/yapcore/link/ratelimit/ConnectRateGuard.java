package com.yapcore.link.ratelimit;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.plugin.LinkMetricsImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Per-IP connect / handshake / login flood protection + concurrent session caps. */
public final class ConnectRateGuard {

    private static final Logger LOG = Logger.getLogger("YaP.Link.RateLimit");

    private final IpRateLimiter connectLimiter = new IpRateLimiter();
    private final IpRateLimiter handshakeLimiter = new IpRateLimiter();
    private final IpRateLimiter loginLimiter = new IpRateLimiter();
    private final ConcurrentHashMap<String, AtomicInteger> concurrent = new ConcurrentHashMap<>();
    private final LinkMetricsImpl metrics;

    public ConnectRateGuard(LinkMetricsImpl metrics) {
        this.metrics = metrics;
    }

    public boolean allowConnect(String ip, LinkConfig cfg) {
        if (!cfg.connectRateLimitEnabled()) {
            return true;
        }
        if (exempt(ip, cfg)) {
            return true;
        }
        boolean ok = connectLimiter.tryAcquire(ip, cfg.connectRatePerIp(), cfg.connectRateWindowMs());
        if (!ok) {
            metrics.counter("connect.throttled", 1);
            LOG.fine("connect throttled ip=" + ip);
        } else {
            metrics.counter("connect.accepted", 1);
        }
        return ok;
    }

    public boolean allowHandshake(String ip, LinkConfig cfg) {
        if (!cfg.handshakeRateLimitEnabled()) {
            return true;
        }
        if (exempt(ip, cfg)) {
            return true;
        }
        boolean ok = handshakeLimiter.tryAcquire(ip, cfg.handshakeRatePerIp(), cfg.handshakeRateWindowMs());
        if (!ok) {
            metrics.counter("handshake.dropped", 1);
            LOG.fine("handshake dropped ip=" + ip);
        }
        return ok;
    }

    public boolean allowLogin(String ip, LinkConfig cfg) {
        if (!cfg.loginRateLimitEnabled()) {
            return true;
        }
        if (exempt(ip, cfg)) {
            return true;
        }
        boolean ok = loginLimiter.tryAcquire(ip, cfg.loginRatePerIp(), cfg.loginRateWindowMs());
        if (!ok) {
            metrics.counter("login.dropped", 1);
            LOG.fine("login dropped ip=" + ip);
        } else {
            metrics.counter("login.attempts", 1);
        }
        return ok;
    }

    /**
     * Reserve one concurrent TCP slot for {@code ip}. Call {@link #releaseConcurrent(String)}
     * when the channel closes (including immediate reject paths that already acquired).
     */
    public boolean tryAcquireConcurrent(String ip, LinkConfig cfg) {
        if (!cfg.maxConcurrentPerIpEnabled() || cfg.maxConcurrentPerIp() <= 0) {
            return true;
        }
        if (exempt(ip, cfg)) {
            return true;
        }
        AtomicInteger n = concurrent.computeIfAbsent(ip, k -> new AtomicInteger());
        int cur = n.incrementAndGet();
        if (cur > cfg.maxConcurrentPerIp()) {
            n.decrementAndGet();
            metrics.counter("connect.concurrent_dropped", 1);
            LOG.fine("concurrent cap ip=" + ip + " n=" + cur);
            return false;
        }
        return true;
    }

    public void releaseConcurrent(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        AtomicInteger n = concurrent.get(ip);
        if (n == null) {
            return;
        }
        int v = n.decrementAndGet();
        if (v <= 0) {
            concurrent.remove(ip, n);
        }
    }

    public java.util.Map<String, Long> snapshot() {
        java.util.LinkedHashMap<String, Long> m = new java.util.LinkedHashMap<>();
        m.put("connect_throttled", metrics.counter("connect.throttled"));
        m.put("connect_accepted", metrics.counter("connect.accepted"));
        m.put("connect_concurrent_dropped", metrics.counter("connect.concurrent_dropped"));
        m.put("handshake_dropped", metrics.counter("handshake.dropped"));
        m.put("login_dropped", metrics.counter("login.dropped"));
        m.put("login_attempts", metrics.counter("login.attempts"));
        m.put("tracked_ips_connect", (long) connectLimiter.size());
        m.put("tracked_ips_concurrent", (long) concurrent.size());
        return m;
    }

    private static boolean exempt(String ip, LinkConfig cfg) {
        return cfg.rateLimitExemptLoopback() && isLoopback(ip);
    }

    static boolean isLoopback(String ip) {
        return ip == null
                || ip.equals("127.0.0.1")
                || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("127.");
    }
}
