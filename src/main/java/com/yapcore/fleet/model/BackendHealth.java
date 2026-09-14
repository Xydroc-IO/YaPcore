package com.yapcore.fleet.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Live Link backend probe row for fleet / Link UI. */
public final class BackendHealth {

    private final String name;
    private final boolean up;
    private final int online;
    private final int max;
    private final long latencyMs;
    private final long checkedAtMs;
    private final String error;
    private final String versionName;
    private final int protocol;
    private final String motd;
    private final String source;

    public BackendHealth(
            String name,
            boolean up,
            int online,
            int max,
            long latencyMs,
            long checkedAtMs,
            String error,
            String versionName,
            int protocol,
            String motd,
            String source) {
        this.name = name == null ? "" : name;
        this.up = up;
        this.online = Math.max(0, online);
        this.max = Math.max(0, max);
        this.latencyMs = latencyMs;
        this.checkedAtMs = checkedAtMs;
        this.error = error;
        this.versionName = versionName == null ? "" : versionName;
        this.protocol = protocol;
        this.motd = motd == null ? "" : motd;
        this.source = source == null ? "unknown" : source;
    }

    public String name() {
        return name;
    }

    public boolean up() {
        return up;
    }

    public int online() {
        return online;
    }

    public int max() {
        return max;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public long checkedAtMs() {
        return checkedAtMs;
    }

    public String error() {
        return error;
    }

    public String versionName() {
        return versionName;
    }

    public int protocol() {
        return protocol;
    }

    public String motd() {
        return motd;
    }

    public String source() {
        return source;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("up", up);
        m.put("online", online);
        m.put("max", max);
        m.put("latencyMs", latencyMs);
        m.put("checkedAtMs", checkedAtMs);
        m.put("error", error);
        m.put("versionName", versionName);
        m.put("protocol", protocol);
        m.put("motd", motd);
        m.put("source", source);
        return Collections.unmodifiableMap(m);
    }

    @SuppressWarnings("unchecked")
    public static BackendHealth fromMap(Map<String, Object> m, String source) {
        if (m == null) {
            return null;
        }
        String name = Objects.toString(m.get("name"), "");
        boolean up = Boolean.TRUE.equals(m.get("up")) || "true".equalsIgnoreCase(String.valueOf(m.get("up")));
        return new BackendHealth(
                name,
                up,
                asInt(m.get("online")),
                asInt(m.get("max")),
                asLong(m.get("latencyMs"), -1L),
                asLong(m.get("checkedAtMs"), 0L),
                m.get("error") == null ? null : String.valueOf(m.get("error")),
                Objects.toString(m.get("versionName"), ""),
                asInt(m.get("protocol")),
                Objects.toString(m.get("motd"), ""),
                source);
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long asLong(Object o, long def) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
