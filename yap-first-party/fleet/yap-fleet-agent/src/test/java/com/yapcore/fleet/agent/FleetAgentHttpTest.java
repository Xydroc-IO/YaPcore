package com.yapcore.fleet.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FleetAgentHttpTest {

    private static final Type MAP = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Gson GSON = new Gson();

    @TempDir
    Path tmp;

    @Test
    void healthRequiresBearerAndReturnsOk() throws Exception {
        String token = "test-token-xyz";
        FleetAgentHttp agent = new FleetAgentHttp(tmp, "node-test", token);
        // Bind ephemeral via start on free port
        int port = freePort();
        agent.start("127.0.0.1", port);
        try {
            HttpURLConnection bad = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/v1/health").toURL().openConnection();
            bad.setConnectTimeout(2000);
            bad.setReadTimeout(2000);
            assertEquals(401, bad.getResponseCode());

            HttpURLConnection ok = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/v1/health").toURL().openConnection();
            ok.setRequestProperty("Authorization", "Bearer " + token);
            ok.setConnectTimeout(2000);
            ok.setReadTimeout(2000);
            assertEquals(200, ok.getResponseCode());
            try (InputStream in = ok.getInputStream()) {
                Map<String, Object> body = GSON.fromJson(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8), MAP);
                assertTrue(Boolean.TRUE.equals(body.get("ok")));
                assertEquals("node-test", body.get("nodeId"));
            }
        } finally {
            agent.stop();
        }
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }
}
