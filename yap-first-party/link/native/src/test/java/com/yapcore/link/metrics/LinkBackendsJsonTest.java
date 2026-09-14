package com.yapcore.link.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.backend.BackendMonitor;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LinkBackendsJsonTest {

    @Test
    void renderIncludesBackendsArray() throws Exception {
        LinkConfig cfg = LinkConfig.load(Files.createTempDirectory("link-backends-json"));
        BackendMonitor mon = new BackendMonitor(cfg);
        String json = LinkBackendsJson.render(mon);
        assertTrue(json.contains("\"backends\""));
        assertTrue(json.contains("ok"));
        Map<String, Object> row = LinkBackendsJson.toMap("lobby", null);
        assertTrue(row.containsKey("latencyMs"));
        assertTrue(row.containsKey("up"));
    }
}
