package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSeenPlayersTest {

    @TempDir
    Path tmp;

    @Test
    void mergesSnapshotUsercacheAndOnline() throws Exception {
        Path plugins = tmp.resolve("plugins").resolve("YaPModeration");
        Files.createDirectories(plugins);
        Files.writeString(plugins.resolve("seen-players.json"),
                "{\"players\":[{\"uuid\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"username\":\"Steve\",\"nickname\":\"Stevie\","
                        + "\"ip\":\"203.0.113.9\",\"ips\":\"203.0.113.9,203.0.113.8\","
                        + "\"firstSeen\":10,\"lastSeen\":20}]}");
        Files.writeString(tmp.resolve("usercache.json"),
                "[{\"name\":\"Alex\",\"uuid\":\"22222222-2222-2222-2222-222222222222\","
                        + "\"expiresOn\":\"2026-01-01\"}]");

        List<Map<String, Object>> seen = DashboardSeenPlayers.load(tmp, List.of(
                Map.of("name", "Steve", "uuid", "11111111-1111-1111-1111-111111111111",
                        "ip", "203.0.113.10", "displayName", "Stevie")));

        assertEquals(2, seen.size());
        Map<String, Object> steve = seen.stream()
                .filter(r -> "Steve".equals(r.get("username")))
                .findFirst().orElseThrow();
        assertEquals("203.0.113.10", steve.get("ip"));
        assertEquals(true, steve.get("online"));
        assertEquals("Stevie", steve.get("nickname"));
        assertEquals(10L, ((Number) steve.get("firstSeen")).longValue());
        assertEquals("203.0.113.9,203.0.113.8", steve.get("ips"));
        Map<String, Object> alex = seen.stream()
                .filter(r -> "Alex".equals(r.get("username")))
                .findFirst().orElseThrow();
        assertEquals("22222222-2222-2222-2222-222222222222", alex.get("uuid"));
        assertTrue(alex.get("ip") == null || "".equals(alex.get("ip")));
    }
}
