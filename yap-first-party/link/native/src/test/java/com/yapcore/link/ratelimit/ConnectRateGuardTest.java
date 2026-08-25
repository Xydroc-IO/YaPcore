package com.yapcore.link.ratelimit;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.plugin.LinkMetricsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectRateGuardTest {

    @TempDir
    Path home;

    @Test
    void throttlesPublicIpAndExemptsLoopbackByDefault() throws Exception {
        LinkConfig cfg = loadConfig("""
                bind=127.0.0.1:25565
                connect-rate-limit-enabled=true
                connect-rate-per-ip=3
                connect-rate-window-ms=60000
                handshake-rate-limit-enabled=false
                login-rate-limit-enabled=false
                max-concurrent-per-ip-enabled=false
                rate-limit-exempt-loopback=true
                metrics-http-enabled=false
                """);
        LinkMetricsImpl metrics = new LinkMetricsImpl();
        ConnectRateGuard guard = new ConnectRateGuard(metrics);

        assertTrue(guard.allowConnect("203.0.113.50", cfg));
        assertTrue(guard.allowConnect("203.0.113.50", cfg));
        assertTrue(guard.allowConnect("203.0.113.50", cfg));
        assertFalse(guard.allowConnect("203.0.113.50", cfg));
        assertEquals(1L, metrics.counter("connect.throttled"));

        // Loopback still accepted under flood (smokes)
        for (int i = 0; i < 20; i++) {
            assertTrue(guard.allowConnect("127.0.0.1", cfg));
        }
    }

    @Test
    void soakModeLoopbackNotExempt() throws Exception {
        LinkConfig cfg = loadConfig("""
                bind=127.0.0.1:25565
                connect-rate-per-ip=2
                connect-rate-window-ms=60000
                handshake-rate-limit-enabled=false
                login-rate-limit-enabled=false
                max-concurrent-per-ip-enabled=false
                rate-limit-exempt-loopback=false
                metrics-http-enabled=false
                """);
        LinkMetricsImpl metrics = new LinkMetricsImpl();
        ConnectRateGuard guard = new ConnectRateGuard(metrics);
        assertTrue(guard.allowConnect("127.0.0.1", cfg));
        assertTrue(guard.allowConnect("127.0.0.1", cfg));
        assertFalse(guard.allowConnect("127.0.0.1", cfg));
        assertTrue(metrics.counter("connect.throttled") >= 1L);
    }

    @Test
    void concurrentCapPerIp() throws Exception {
        LinkConfig cfg = loadConfig("""
                bind=127.0.0.1:25565
                connect-rate-limit-enabled=false
                handshake-rate-limit-enabled=false
                login-rate-limit-enabled=false
                max-concurrent-per-ip-enabled=true
                max-concurrent-per-ip=2
                rate-limit-exempt-loopback=true
                metrics-http-enabled=false
                """);
        LinkMetricsImpl metrics = new LinkMetricsImpl();
        ConnectRateGuard guard = new ConnectRateGuard(metrics);
        String ip = "198.51.100.9";
        assertTrue(guard.tryAcquireConcurrent(ip, cfg));
        assertTrue(guard.tryAcquireConcurrent(ip, cfg));
        assertFalse(guard.tryAcquireConcurrent(ip, cfg));
        assertEquals(1L, metrics.counter("connect.concurrent_dropped"));
        guard.releaseConcurrent(ip);
        assertTrue(guard.tryAcquireConcurrent(ip, cfg));
    }

    private LinkConfig loadConfig(String body) throws Exception {
        Files.writeString(home.resolve("link.properties"), body
                + "\nservers.lobby=127.0.0.1:25566\ntry=lobby\n"
                + "player-info-forwarding-mode=modern\nforwarding-secret-file=forwarding.secret\n");
        return LinkConfig.load(home);
    }
}
