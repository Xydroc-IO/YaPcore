package com.yapcore.fleet.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class AgentProtocolTest {

    @Test
    void bearerExtractAndJsonRoundTrip() {
        assertEquals("abc", AgentProtocol.extractBearer("Bearer abc"));
        assertEquals("abc", AgentProtocol.extractBearer("bearer abc"));
        assertEquals(null, AgentProtocol.extractBearer("Basic x"));

        Map<String, Object> health = AgentProtocol.healthOk("node-1");
        assertTrue(Boolean.TRUE.equals(health.get("ok")));
        assertEquals("node-1", health.get("nodeId"));

        String json = AgentProtocol.toJson(AgentProtocol.error("nope"));
        Map<String, Object> parsed = AgentProtocol.parseJsonObject(json);
        assertFalse(Boolean.TRUE.equals(parsed.get("ok")));
        assertEquals("nope", parsed.get("error"));
    }
}
