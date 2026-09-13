package com.yapcore.link;

/**
 * Edge rate-limit and metrics HTTP accessors (split from {@link LinkConfig}).
 */
final class LinkConfigRateLimits {

    private LinkConfigRateLimits() {}

    static boolean connectRateLimitEnabled(LinkConfig cfg) {
        return cfg.bool("connect-rate-limit-enabled", true);
    }

    static int connectRatePerIp(LinkConfig cfg) {
        return Math.max(1, cfg.intProp("connect-rate-per-ip", 20));
    }

    static long connectRateWindowMs(LinkConfig cfg) {
        return Math.max(100L, cfg.intProp("connect-rate-window-ms", 10_000));
    }

    static boolean handshakeRateLimitEnabled(LinkConfig cfg) {
        return cfg.bool("handshake-rate-limit-enabled", true);
    }

    static int handshakeRatePerIp(LinkConfig cfg) {
        return Math.max(1, cfg.intProp("handshake-rate-per-ip", 40));
    }

    static long handshakeRateWindowMs(LinkConfig cfg) {
        return Math.max(100L, cfg.intProp("handshake-rate-window-ms", 10_000));
    }

    static boolean loginRateLimitEnabled(LinkConfig cfg) {
        return cfg.bool("login-rate-limit-enabled", true);
    }

    static int loginRatePerIp(LinkConfig cfg) {
        return Math.max(1, cfg.intProp("login-rate-per-ip", 10));
    }

    static long loginRateWindowMs(LinkConfig cfg) {
        return Math.max(100L, cfg.intProp("login-rate-window-ms", 10_000));
    }

    /** When true (default), 127.0.0.1 / ::1 skip rate + concurrent limits (smokes / local ops). */
    static boolean rateLimitExemptLoopback(LinkConfig cfg) {
        return cfg.bool("rate-limit-exempt-loopback", true);
    }

    static boolean maxConcurrentPerIpEnabled(LinkConfig cfg) {
        return cfg.bool("max-concurrent-per-ip-enabled", true);
    }

    /** Max simultaneous TCP sessions per remote IP; {@code 0} disables. */
    static int maxConcurrentPerIp(LinkConfig cfg) {
        return Math.max(0, cfg.intProp("max-concurrent-per-ip", 8));
    }

    static boolean metricsHttpEnabled(LinkConfig cfg) {
        return cfg.bool("metrics-http-enabled", true);
    }

    static String metricsHttpBind(LinkConfig cfg) {
        String h = cfg.props.getProperty("metrics-http-bind", "127.0.0.1").trim();
        return h.isEmpty() ? "127.0.0.1" : h;
    }

    static int metricsHttpPort(LinkConfig cfg) {
        return cfg.intProp("metrics-http-port", 9091);
    }

}
