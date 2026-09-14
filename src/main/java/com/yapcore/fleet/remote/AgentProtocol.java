package com.yapcore.fleet.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared request/response shapes for chassis ↔ yap-fleet-agent. */
public final class AgentProtocol {

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private AgentProtocol() {
    }

    public static Map<String, Object> parseJsonObject(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> m = GSON.fromJson(body, MAP_TYPE);
        return m == null ? new LinkedHashMap<>() : m;
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static Map<String, Object> healthOk(String nodeId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("nodeId", nodeId);
        m.put("role", "yap-fleet-agent");
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message == null ? "error" : message);
        return m;
    }

    public static Map<String, Object> okAction(String action, String instanceId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("action", action);
        m.put("instanceId", instanceId);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asObjectList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) {
            return out;
        }
        for (Object el : list) {
            if (el instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    public static String extractBearer(String header) {
        if (header == null) {
            return null;
        }
        String h = header.trim();
        if (h.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return h.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
