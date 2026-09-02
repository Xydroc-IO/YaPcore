package com.yapcore.perms;

import java.util.Locale;
import java.util.Map;

/**
 * LuckPerms-class wildcard resolution: exact nodes win, then the most specific
 * wildcard ({@code *} / {@code foo.*} / {@code foo*}). At equal specificity, deny wins.
 */
public final class PermissionNodes {

    private PermissionNodes() {
    }

    public static boolean has(Map<String, Boolean> nodes, String node) {
        if (nodes == null || node == null || node.isBlank()) {
            return false;
        }
        String target = node.toLowerCase(Locale.ROOT);
        Boolean exact = exactValue(nodes, target);
        if (exact != null) {
            return exact;
        }
        int bestSpec = -1;
        Boolean best = null;
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            int spec = matchSpecificity(entry.getKey(), target);
            if (spec < 0) {
                continue;
            }
            if (spec > bestSpec) {
                bestSpec = spec;
                best = entry.getValue();
            } else if (spec == bestSpec && Boolean.FALSE.equals(entry.getValue())) {
                best = false;
            }
        }
        return Boolean.TRUE.equals(best);
    }

    /** Explain which stored node decided {@code node}, or empty if undefined. */
    public static String decidingPattern(Map<String, Boolean> nodes, String node) {
        if (nodes == null || node == null) {
            return "";
        }
        String target = node.toLowerCase(Locale.ROOT);
        if (exactValue(nodes, target) != null) {
            return target;
        }
        int bestSpec = -1;
        String best = "";
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            int spec = matchSpecificity(entry.getKey(), target);
            if (spec < 0) {
                continue;
            }
            if (spec > bestSpec || (spec == bestSpec && Boolean.FALSE.equals(entry.getValue()))) {
                bestSpec = spec;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static Boolean exactValue(Map<String, Boolean> nodes, String target) {
        Boolean exact = nodes.get(target);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * @return -1 if no match; otherwise specificity (higher = more specific).
     *         Exact is not handled here. {@code *} is 0; {@code a.b.*} is length of {@code a.b.}.
     */
    static int matchSpecificity(String pattern, String node) {
        if (pattern == null || node == null) {
            return -1;
        }
        String p = pattern.toLowerCase(Locale.ROOT);
        if (p.equals(node)) {
            return Integer.MAX_VALUE;
        }
        if (p.equals("*")) {
            return 0;
        }
        if (p.endsWith(".*")) {
            String prefix = p.substring(0, p.length() - 1); // keep trailing '.'
            return node.startsWith(prefix) ? prefix.length() : -1;
        }
        if (p.endsWith("*")) {
            String prefix = p.substring(0, p.length() - 1);
            return node.startsWith(prefix) ? prefix.length() : -1;
        }
        return -1;
    }
}
