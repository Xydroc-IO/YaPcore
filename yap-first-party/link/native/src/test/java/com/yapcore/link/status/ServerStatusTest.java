package com.yapcore.link.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerStatusTest {

    @Test
    void parseAndOverrideCounts() {
        String json = """
                {"version":{"name":"Folia","protocol":776},"players":{"max":20,"online":5},"description":{"text":"Hello"}}
                """;
        ServerStatus s = ServerStatus.parseJson(json);
        assertEquals(5, s.online());
        assertEquals(20, s.max());
        assertEquals("Folia", s.versionName());
        assertEquals(776, s.protocol());
        String out = s.toStatusJson(12, 100);
        assertTrue(out.contains("\"online\":12"));
        assertTrue(out.contains("\"max\":100"));
    }
}
