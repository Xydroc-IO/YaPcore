package com.yapcore.perms;

import java.util.Locale;
import java.util.Map;

/** Wildcard-aware permission checks for API consumers. */
public final class PermissionNodes {

    private PermissionNodes() {
    }

    public static boolean has(Map<String, Boolean> nodes, String node) {
        if (nodes == null || node == null) {
            return false;
        }
        Boolean exact = nodes.get(node);
        if (exact != null) {
            return exact;
        }
        String lower = node.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (matches(entry.getKey(), lower) && entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String node) {
        if (pattern == null) {
            return false;
        }
        String p = pattern.toLowerCase(Locale.ROOT);
        if (p.equals(node)) {
            return true;
        }
        if (p.endsWith(".*")) {
            String prefix = p.substring(0, p.length() - 1);
            return node.startsWith(prefix);
        }
        if (p.endsWith("*")) {
            String prefix = p.substring(0, p.length() - 1);
            return node.startsWith(prefix);
        }
        return false;
    }
}
