package com.yapcore.fleet.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Remote fleet agent endpoint (token lives under fleet/agents/{id}.token). */
public final class FleetNode {

    private final String id;
    private final String baseUrl;
    private final String tokenRef;
    private final String displayName;

    public FleetNode(String id, String baseUrl, String tokenRef, String displayName) {
        this.id = requireId(id);
        this.baseUrl = requireUrl(baseUrl);
        this.tokenRef = tokenRef == null || tokenRef.isBlank()
                ? "fleet/agents/" + this.id + ".token"
                : tokenRef.trim();
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName.trim();
    }

    public String id() {
        return id;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String tokenRef() {
        return tokenRef;
    }

    public String displayName() {
        return displayName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("baseUrl", baseUrl);
        m.put("tokenRef", tokenRef);
        m.put("displayName", displayName);
        return Collections.unmodifiableMap(m);
    }

    public static FleetNode fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        String id = Objects.toString(m.get("id"), "").trim();
        if (id.isEmpty() || "local".equalsIgnoreCase(id)) {
            return null;
        }
        return new FleetNode(
                id,
                Objects.toString(m.get("baseUrl"), ""),
                Objects.toString(m.getOrDefault("tokenRef", ""), ""),
                Objects.toString(m.getOrDefault("displayName", id), id));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id required");
        }
        String cleaned = id.trim().toLowerCase();
        if (!cleaned.matches("[a-z0-9][a-z0-9_-]{0,31}") || "local".equals(cleaned)) {
            throw new IllegalArgumentException("invalid node id: " + id);
        }
        return cleaned;
    }

    private static String requireUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("node baseUrl required");
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw new IllegalArgumentException("node baseUrl must be http(s): " + url);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
