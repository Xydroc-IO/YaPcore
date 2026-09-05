package com.yapcore.moderation.seen;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenPlayerRepositoryUtilTest {

    @Test
    void parseUuidAcceptsDashedAndCompact() {
        UUID expected = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertEquals(expected, SeenPlayerRepository.parseUuid("550e8400-e29b-41d4-a716-446655440000"));
        assertEquals(expected, SeenPlayerRepository.parseUuid("550e8400e29b41d4a716446655440000"));
        assertNull(SeenPlayerRepository.parseUuid("garbage"));
    }

    @Test
    void looksLikeIpHeuristics() {
        assertTrue(SeenPlayerRepository.looksLikeIp("192.168.1.1"));
        assertFalse(SeenPlayerRepository.looksLikeIp("999.1.1.1"));
        assertTrue(SeenPlayerRepository.looksLikeIp("::1"));
        assertFalse(SeenPlayerRepository.looksLikeIp("Steve"));
    }

    @Test
    void stripColorAndNickname() {
        assertEquals("Hello", SeenPlayerRepository.stripColor("§aHello"));
        assertEquals("", SeenPlayerRepository.nicknameOrEmpty("Steve", "§cSteve"));
        assertEquals("Hero", SeenPlayerRepository.nicknameOrEmpty("Steve", "§bHero"));
    }
}
