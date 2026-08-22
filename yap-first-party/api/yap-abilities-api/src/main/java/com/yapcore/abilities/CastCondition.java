package com.yapcore.abilities;

import java.util.Map;

public record CastCondition(ConditionKind kind, Map<String, String> params) {

    public CastCondition {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public String param(String key) {
        return params.get(key);
    }

    public String param(String key, String fallback) {
        String v = params.get(key);
        return v == null ? fallback : v;
    }

    public int intParam(String key, int fallback) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
