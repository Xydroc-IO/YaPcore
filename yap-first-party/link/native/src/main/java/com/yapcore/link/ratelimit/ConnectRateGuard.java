package com.yapcore.link.ratelimit;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.plugin.LinkMetricsImpl;

import java.util.logging.Logger;

/** Per-IP connect + handshake flood protection for YaP Link. */
public final class ConnectRateGuard {

    private static final Logger LOG = Logger.getLogger("YaP.Link.RateLimit");

    private final IpRateLimiter connectLimiter = new IpRateLimiter();
    private final IpRateLimiter handshakeLimiter = new IpRateLimiter();
    private final IpRateLimiter loginLimiter = new IpRateLimiter();
    private final LinkMetricsImpl metrics;

    public ConnectRateGuard(LinkMetricsImpl metrics) {
        this.metrics = metrics;
    }

    public boolean allowConnect(String ip, LinkConfig cfg) {
        if (!cfg.connectRateLimitEnabled()) {
            return true;
        }
        if (isLoopback(ip)) {
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
        if (isLoopback(ip)) {
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
        if (isLoopback(ip)) {
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

    public java.util.Map<String, Long> snapshot() {
        java.util.LinkedHashMap<String, Long> m = new java.util.LinkedHashMap<>();
        m.put("connect_throttled", metrics.counter("connect.throttled"));
        m.put("connect_accepted", metrics.counter("connect.accepted"));
        m.put("handshake_dropped", metrics.counter("handshake.dropped"));
        m.put("login_dropped", metrics.counter("login.dropped"));
        m.put("login_attempts", metrics.counter("login.attempts"));
        m.put("tracked_ips_connect", (long) connectLimiter.size());
        return m;
    }

    private static boolean isLoopback(String ip) {
        return ip == null
                || ip.equals("127.0.0.1")
                || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("127.");
    }
}
