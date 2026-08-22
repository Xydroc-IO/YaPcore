package com.yapcore.abilities;

import java.util.Map;

public record AbilityEffect(EffectKind kind, Map<String, String> params) {

    public AbilityEffect {
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

    public double doubleParam(String key, double fallback) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean boolParam(String key, boolean fallback) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(v.trim()) || "yes".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }
}
